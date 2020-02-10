/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.wasm.internal

@WasmImport("runtime", "String_getLiteral")
internal fun stringLiteral(index: Int): String =
    implementedAsIntrinsic


internal fun throwWithMessageStub(s: String): Nothing {
    wasm_unreachable()
}

@WasmReinterpret
internal fun unsafeNotNull(x: Any?): Any =
    implementedAsIntrinsic

internal fun nullableEquals(lhs: Any?, rhs: Any?): Boolean {
    if (wasm_ref_is_null(lhs))
        return wasm_ref_is_null(rhs)
    return unsafeNotNull(lhs).equals(rhs)
}

internal fun anyNtoString(x: Any?): String = x.toString()

internal fun nullableFloatIeee754Equals(lhs: Float?, rhs: Float?): Boolean {
    if (lhs == null) return rhs == null
    if (rhs == null) return false
    return wasm_f32_eq(lhs, rhs).reinterpretAsBoolean()
}

internal fun nullableDoubleIeee754Equals(lhs: Double?, rhs: Double?): Boolean {
    if (lhs == null) return rhs == null
    if (rhs == null) return false
    return wasm_f64_eq(lhs, rhs).reinterpretAsBoolean()
}


fun <T:Any> ensureNotNull(v: T?): T = if (v == null) THROW_NPE() else v

@ExcludedFromCodegen
internal fun <T, R> boxIntrinsic(x: T): R =
    implementedAsIntrinsic

@ExcludedFromCodegen
internal fun <T, R> unboxIntrinsic(x: T): R =
    implementedAsIntrinsic
