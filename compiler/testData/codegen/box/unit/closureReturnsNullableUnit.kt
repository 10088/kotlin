// IGNORE_BACKEND: WASM
fun isNull(x: Unit?) = x == null

fun box(): String {
    val closure: () -> Unit? = { null }
    if (!isNull(closure())) return "Fail 1"

    return "OK"
}

// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ WASM_FUNCTION_REFERENCES_UNSUPPORTED
