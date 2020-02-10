/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress(
    "NON_ABSTRACT_FUNCTION_WITH_NO_BODY",
    "MUST_BE_INITIALIZED_OR_BE_ABSTRACT",
    "EXTERNAL_TYPE_EXTENDS_NON_EXTERNAL_TYPE",
    "PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED",
    "WRONG_MODIFIER_TARGET",
    "RESERVED_MEMBER_INSIDE_INLINE_CLASS"
)

package kotlin

import kotlin.wasm.internal.*
import kotlin.wasm.internal.ExcludedFromCodegen
import kotlin.wasm.internal.SkipRTTI
import kotlin.wasm.internal.WasmBinaryOp
import kotlin.wasm.internal.WasmReinterpret

/**
 * Represents a value which is either `true` or `false`. On the JVM, non-nullable values of this type are
 * represented as values of the primitive type `boolean`.
 */
@Suppress("NON_PUBLIC_PRIMARY_CONSTRUCTOR_OF_INLINE_CLASS")
public inline class Boolean @ExcludedFromCodegen private constructor(val value: Boolean) : Comparable<Boolean> {
    /**
     * Returns the inverse of this boolean.
     */
    @WasmUnaryOp(WasmUnaryOp.I32_EQZ)
    public operator fun not(): Boolean =
        implementedAsIntrinsic

    /**
     * Performs a logical `and` operation between this Boolean and the [other] one. Unlike the `&&` operator,
     * this function does not perform short-circuit evaluation. Both `this` and [other] will always be evaluated.
     */
    @WasmBinaryOp(WasmBinaryOp.I32_AND)
    public infix fun and(other: Boolean): Boolean =
        implementedAsIntrinsic

    /**
     * Performs a logical `or` operation between this Boolean and the [other] one. Unlike the `||` operator,
     * this function does not perform short-circuit evaluation. Both `this` and [other] will always be evaluated.
     */
    @WasmBinaryOp(WasmBinaryOp.I32_OR)
    public infix fun or(other: Boolean): Boolean =
        implementedAsIntrinsic

    /**
     * Performs a logical `xor` operation between this Boolean and the [other] one.
     */
    @WasmBinaryOp(WasmBinaryOp.I32_XOR)
    public infix fun xor(other: Boolean): Boolean =
        implementedAsIntrinsic

    public override fun compareTo(other: Boolean): Int =
        wasm_i32_compareTo(this.asInt(), other.asInt())

    override fun toString(): String =
        if (this) "true" else "false"

    override fun hashCode(): Int =
        if (this) 1 else 0

    override fun equals(other: Any?): Boolean {
        val result = if (other !is Boolean) {
            false
        } else {
            this === (other as Boolean)
        }
        return result
    }

    @WasmReinterpret
    internal fun asInt(): Int =
        implementedAsIntrinsic

    @SinceKotlin("1.3")
    companion object {}
}
