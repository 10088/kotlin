// IGNORE_BACKEND: WASM
// IGNORE_BACKEND_FIR: JVM_IR
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME

val xs = "abcd"

fun box(): String {
    var count = 0

    for ((_, _) in xs.withIndex()) {
        count++
    }

    return if (count == 4) "OK" else "fail: '$count'"
}
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ withIndex 
