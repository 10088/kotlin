// IGNORE_BACKEND: WASM
// IGNORE_BACKEND: JS_IR
// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS, NATIVE

package demo_long

fun Long?.inv() : Long = this!!.inv()

fun box() : String {
    val x : Long? = 10
    System.out?.println(x.inv())
    return if(x.inv() == -11.toLong()) "OK" else "fail"
}

// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ System 
