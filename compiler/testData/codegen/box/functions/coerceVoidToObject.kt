// IGNORE_BACKEND: WASM
fun a(): String? = null

fun b(): Nothing = throw Exception()

fun foo(): String = a() ?: b()


fun box(): String {
    try {
        foo()
    } catch (e: Exception) {
        return "OK"
    }

    return "Fail"
}

// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ Exception 
