/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.ir2wasm

import org.jetbrains.kotlin.backend.common.ir.isOverridableOrOverrides
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.codegen.ConstantDataIntArray
import org.jetbrains.kotlin.backend.wasm.codegen.ConstantDataIntField
import org.jetbrains.kotlin.backend.wasm.codegen.ConstantDataStruct
import org.jetbrains.kotlin.backend.wasm.codegen.interfaces.WasmModuleCodegenContext
import org.jetbrains.kotlin.backend.wasm.utils.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.wasm.ir.*

class DeclarationGenerator(val context: WasmModuleCodegenContext) : IrElementVisitorVoid {

    // Shortcuts
    private val backendContext: WasmBackendContext = context.backendContext
    private val irBuiltIns: IrBuiltIns = backendContext.irBuiltIns

    override fun visitElement(element: IrElement) {
        error("Unexpected element of type ${element::class}")
    }

    override fun visitTypeAlias(declaration: IrTypeAlias) {
        // Type aliases are not material
    }

    override fun visitFunction(declaration: IrFunction) {
        // Inline class constructors are currently empty
        if (declaration is IrConstructor)
            if (backendContext.inlineClassesUtils.isClassInlineLike(declaration.parentAsClass))
                return

        // Generate function type
        val watName = declaration.fqNameWhenAvailable.toString()
        val irParameters = declaration.getEffectiveValueParameters()
        val wasmFunctionType =
            WasmFunctionType(
                watName,
                parameterTypes = irParameters.map { context.transformValueParameterType(it) },
                resultType = context.transformResultType(declaration.returnType)
            )
        context.defineFunctionType(declaration.symbol, wasmFunctionType)

        val isIntrinsic = declaration.hasWasmReinterpretAnnotation() ||
                declaration.getWasmUnaryOpAnnotation() != null ||
                declaration.getWasmBinaryOpAnnotation() != null ||
                declaration.getWasmRefOpAnnotation() != null

        if (declaration is IrSimpleFunction) {
            if (declaration.modality == Modality.ABSTRACT) return
            if (declaration.isFakeOverride) return

            val isVirtual = declaration.isOverridableOrOverrides

            if (isVirtual) {
                // Register function as virtual, meaning this function
                // will be stored Wasm table and could be called indirectly.
                context.registerVirtualFunction(declaration.symbol)
            }

            if (!isVirtual && isIntrinsic) {
                // Calls to non-virtual intrinsic functions are replaced with something else.
                // No need to generate them.
                return
            }
        }

        assert(declaration == declaration.realOverrideTarget) {
            "Sanity check that $declaration is a real function that can be used in calls"
        }

        val importedName = declaration.getWasmImportAnnotation()
        if (importedName != null) {
            // Imported functions don't have bodies. Declaring the signature:
            context.defineFunction(
                declaration.symbol,
                WasmImportedFunction(watName, wasmFunctionType, importedName)
            )
            // TODO: Support re-export of imported functions.
            return
        }

        val function = WasmDefinedFunction(watName, wasmFunctionType)
        val functionCodegenContext = WasmFunctionCodegenContextImpl(
            declaration,
            function,
            backendContext,
            context
        )

        for (irParameter in irParameters) {
            functionCodegenContext.defineLocal(irParameter.symbol)
        }

        val exprGen = functionCodegenContext.bodyGen
        val bodyBuilder = BodyGenerator(functionCodegenContext)

        if (isIntrinsic) {
            bodyBuilder.tryToGenerateWasmOpIntrinsicCall(declaration)
        } else {
            when (val body = declaration.body) {
                is IrBlockBody ->
                    for (statement in body.statements) {
                        bodyBuilder.statementToWasmInstruction(statement)
                    }

                is IrExpressionBody ->
                    bodyBuilder.generateExpression(body.expression)

                else -> error("Unexpected body $body")
            }
        }

        // Return implicit this from constructions to avoid extra tmp
        // variables on constructor call sites.
        // TODO: Redesign construction scheme.
        if (declaration is IrConstructor) {
            exprGen.buildGetLocal(/*implicit this*/ function.locals[0])
            exprGen.buildReturn()
        }

        // Add unreachable if function returns something but not as a last instruction.
        if (wasmFunctionType.resultType != null && declaration.body is IrBlockBody) {
            exprGen.buildUnreachable()
        }

        context.defineFunction(declaration.symbol, function)

        if (declaration == backendContext.startFunction)
            context.setStartFunction(function)

        if (declaration.isExported(backendContext)) {
            context.addExport(
                WasmExport(
                    function = function,
                    exportedName = declaration.name.identifier,
                    kind = WasmExport.Kind.FUNCTION
                )
            )
        }
    }

    override fun visitClass(declaration: IrClass) {
        if (declaration.isAnnotationClass) return
        val symbol = declaration.symbol

        if (declaration.isInterface) {
            context.registerInterface(symbol)
        } else {
            val structType = WasmStructType(
                name = declaration.fqNameWhenAvailable.toString(),
                fields = declaration.allFields(irBuiltIns).map {
                    WasmStructFieldDeclaration(
                        name = it.name.toString(),
                        type = context.transformType(it.type),
                        isMutable = true
                    )
                }
            )
            context.defineStructType(symbol, structType)
            context.registerClass(symbol)
            context.generateTypeInfo(symbol, binaryDataStruct(context.getClassMetadata(symbol)))
        }

        for (member in declaration.declarations) {
            member.acceptVoid(this)
        }
    }

    private fun binaryDataStruct(classMetadata: ClassMetadata): ConstantDataStruct {
        val invalidIndex = -1
        val superClass = classMetadata.superClass?.klass

        val superClassSymbol: WasmSymbol<Int> =
            superClass?.let { context.referenceClassId(it.symbol) } ?: WasmSymbol(invalidIndex)

        val superTypeField =
            ConstantDataIntField("Super class", superClassSymbol)

        val interfacesArray = ConstantDataIntArray(
            "data",
            classMetadata.interfaces.map { context.referenceInterfaceId(it.symbol) }
        )
        val interfacesArraySize = ConstantDataIntField(
            "size",
            interfacesArray.value.size
        )

        val implementedInterfacesArrayWithSize = ConstantDataStruct(
            "Implemented interfaces array",
            listOf(interfacesArraySize, interfacesArray)
        )

        val vtableSizeField = ConstantDataIntField(
            "V-table length",
            classMetadata.virtualMethods.size
        )

        val vtableArray = ConstantDataIntArray(
            "V-table",
            classMetadata.virtualMethods.map {
                if (it.function.modality == Modality.ABSTRACT) {
                    WasmSymbol(invalidIndex)
                } else {
                    context.referenceVirtualFunctionId(it.function.symbol)
                }
            }
        )

        val signaturesArray = ConstantDataIntArray(
            "Signatures",
            classMetadata.virtualMethods.map {
                if (it.function.modality == Modality.ABSTRACT) {
                    WasmSymbol(invalidIndex)
                } else {
                    context.referenceSignatureId(it.signature)
                }
            }
        )

        return ConstantDataStruct(
            "Class TypeInfo: ${classMetadata.klass.fqNameWhenAvailable} ",
            listOf(
                superTypeField,
                vtableSizeField,
                vtableArray,
                signaturesArray,
                implementedInterfacesArrayWithSize,
            )
        )
    }

    override fun visitField(declaration: IrField) {
        // Member fields are generated as part of struct type
        if (!declaration.isStatic) return

        val wasmType = context.transformType(declaration.type)

        val initBody = mutableListOf<WasmInstr>()
        val wasmExpressionGenerator = WasmIrExpressionBuilder(initBody)
        generateDefaultInitializerForType(wasmType, wasmExpressionGenerator)

        val global = WasmGlobal(
            name = declaration.fqNameWhenAvailable.toString(),
            type = wasmType,
            isMutable = true,
            // All globals are currently initialized in start function
            init = initBody
        )

        context.defineGlobal(declaration.symbol, global)
    }
}


fun generateDefaultInitializerForType(type: WasmValueType, g: WasmExpressionBuilder) = when (type) {
    WasmI32 -> g.buildConstI32(0)
    WasmI1 -> g.buildConstI32(0)
    WasmI64 -> g.buildConstI64(0)
    WasmF32 -> g.buildConstF32(0f)
    WasmF64 -> g.buildConstF64(0.0)
    WasmAnyRef -> g.buildRefNull()
    is WasmStructRef -> g.buildRefNull()
    WasmNullRefType -> g.buildRefNull()
    WasmUnreachableType -> error("Unreachable type can't be initialized")
    else -> error("Unknown value type")
}

fun IrFunction.getEffectiveValueParameters(): List<IrValueParameter> {
    val implicitThis = if (this is IrConstructor) parentAsClass.thisReceiver!! else null
    return listOfNotNull(implicitThis, dispatchReceiverParameter, extensionReceiverParameter) + valueParameters
}

fun IrFunction.isExported(context: WasmBackendContext): Boolean =
    visibility == Visibilities.PUBLIC && fqNameWhenAvailable in context.additionalExportedDeclarations