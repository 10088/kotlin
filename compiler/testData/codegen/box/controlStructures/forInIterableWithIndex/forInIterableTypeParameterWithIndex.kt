// IGNORE_BACKEND: WASM
// IGNORE_BACKEND_FIR: JVM_IR
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME

val xs = listOf("a", "b", "c", "d")

fun <T : Iterable<*>> test(iterable: T): String {
    val s = StringBuilder()

    for ((index, x) in iterable.withIndex()) {
        s.append("$index:$x;")
    }

    return s.toString()
}

fun box(): String {
    val ss = test(xs)
    return if (ss == "0:a;1:b;2:c;3:d;") "OK" else "fail: '$ss'"
}
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ listOf 
