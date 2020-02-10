// !LANGUAGE: +MultiPlatformProjects
// IGNORE_BACKEND: WASM
// IGNORE_BACKEND_FIR: JVM_IR
// IGNORE_BACKEND: NATIVE
// WITH_RUNTIME
// WITH_COROUTINES

// FILE: lib.kt

expect interface I {
    suspend fun f(p: Int = 1): String
}

// FILE: main.kt
import kotlin.coroutines.*
import helpers.*

actual interface I {
    actual suspend fun f(p: Int): String
}

class II : I {
    override suspend fun f(p: Int): String = "OK"
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res = "FAIL"
    builder {
        res = II().f()
    }
    return res
}
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ intrinsics 
