// IGNORE_BACKEND: WASM
// IGNORE_BACKEND_FIR: JVM_IR
// KJS_WITH_FULL_RUNTIME
// !LANGUAGE: +NewInference
// WITH_RUNTIME

// FILE: messages/foo.kt

package messages

fun foo() {}

// FILE: sample.kt

class Test {
    val messages = arrayListOf<String>()

    fun test(): Boolean {
        return messages.any { it == "foo" }
    }
}

fun box(): String {
    val result = Test().test()
    return if (result) "faile" else "OK"
}
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ arrayListOf 
