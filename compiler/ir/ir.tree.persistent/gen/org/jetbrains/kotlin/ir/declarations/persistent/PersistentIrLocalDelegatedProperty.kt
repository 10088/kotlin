/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.persistent

import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrLocalDelegatedProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.declarations.persistent.carriers.Carrier
import org.jetbrains.kotlin.ir.declarations.persistent.carriers.LocalDelegatedPropertyCarrier
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrLocalDelegatedPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.Name

// Auto-generated by compiler/ir/ir.tree.persistent/generator/src/org/jetbrains/kotlin/ir/persistentIrGenerator/Main.kt. DO NOT EDIT!

// TODO make not persistent
internal class PersistentIrLocalDelegatedProperty(
    override val startOffset: Int,
    override val endOffset: Int,
    origin: IrDeclarationOrigin,
    override val symbol: IrLocalDelegatedPropertySymbol,
    override val name: Name,
    type: IrType,
    override val isVar: Boolean,
    override val factory: PersistentIrFactory
) : IrLocalDelegatedProperty(),
    PersistentIrDeclarationBase<LocalDelegatedPropertyCarrier>,
    LocalDelegatedPropertyCarrier {

    init {
        symbol.bind(this)
    }

    override var signature: IdSignature? = factory.currentSignature(this)

    override var lastModified: Int = factory.stageController.currentStage
    override var loweredUpTo: Int = factory.stageController.currentStage
    override var values: Array<Carrier>? = null
    override val createdOn: Int = factory.stageController.currentStage

    override var parentField: IrDeclarationParent? = null
    override var originField: IrDeclarationOrigin = origin
    override var removedOn: Int = Int.MAX_VALUE
    override var annotationsField: List<IrConstructorCall> = emptyList()
    private val hashCodeValue: Int = PersistentIrDeclarationBase.hashCodeCounter++
    override fun hashCode(): Int = hashCodeValue
    override fun equals(other: Any?): Boolean = (this === other)

    override var origin: IrDeclarationOrigin
        get() = super.origin
        set(value) {
            super.origin = value
        }

    override var parent: IrDeclarationParent
        get() = super.parent
        set(value) {
            super.parent = value
        }

    override var annotations: List<IrConstructorCall>
        get() = super.annotations
        set(value) {
            super.annotations = value
        }

    @ObsoleteDescriptorBasedAPI
    override val descriptor: VariableDescriptorWithAccessors
        get() = symbol.descriptor

    override var typeField: IrType = type

    override var type: IrType
        get() = getCarrier().typeField
        set(v) {
            if (type !== v) {
                setCarrier()
                typeField = v
            }
        }

    override var delegateField: IrVariable? = null

    override var delegate: IrVariable
        get() = getCarrier().delegateField!!
        set(v) {
            if (getCarrier().delegateField !== v) {
                setCarrier()
                delegateField = v
            }
        }

    override var getterField: IrSimpleFunction? = null

    override var getterSymbolField: IrSimpleFunctionSymbol?
        get() = getterField?.symbol
        set(v) {
            getterField = v?.owner
        }

    override var getter: IrSimpleFunction
        get() = getCarrier().getterField!!
        set(v) {
            if (getCarrier().getterField !== v) {
                setCarrier()
                getterField = v
            }
        }

    override var setterField: IrSimpleFunction? = null

    override var setterSymbolField: IrSimpleFunctionSymbol?
        get() = setterField?.symbol
        set(v) {
            setterField = v?.owner
        }

    override var setter: IrSimpleFunction?
        get() = getCarrier().setterField
        set(v) {
            if (setter !== v) {
                setCarrier()
                setterField = v
            }
        }

    override var metadata: MetadataSource? = null
}
