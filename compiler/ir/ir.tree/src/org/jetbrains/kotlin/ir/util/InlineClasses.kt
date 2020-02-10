/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*


fun getInlineClassUnderlyingType(irClass: IrClass): IrType {
    for (declaration in irClass.declarations) {
        if (declaration is IrConstructor && declaration.isPrimary) {
            return declaration.valueParameters[0].type
        }
    }
    error("Inline class has no primary constructor: ${irClass.fqNameWhenAvailable}")
}

fun getInlineClassBackingField(irClass: IrClass): IrField {
    for (declaration in irClass.declarations) {
        if (declaration is IrField)
            return declaration

        if (declaration is IrProperty)
            return declaration.backingField ?: continue
    }
    error("Inline class has no field: ${irClass.fqNameWhenAvailable}")
}
