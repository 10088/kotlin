// !LANGUAGE: +InlineClasses
// IGNORE_BACKEND: WASM
// IGNORE_BACKEND_FIR: JVM_IR

var setterInvoked = 0
var backing = 42

inline class Delegate(val ignored: Int) {

    operator fun getValue(thisRef: Any?, prop: Any?) =
        backing

    operator fun setValue(thisRef: Any?, prop: Any?, newValue: Int) {
        setterInvoked++
        backing = newValue
    }
}

var topLevelD by Delegate(0)

fun box(): String {
    if (topLevelD != 42) AssertionError()

    topLevelD = 1234
    if (topLevelD != 1234) throw AssertionError()
    if (backing != 1234) throw AssertionError()

    if (setterInvoked != 1) throw AssertionError()

    return "OK"
}
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: PROPERTY_REFERENCE
