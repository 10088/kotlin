fun zap(s: String) = s

inline fun tryZap(string: String, fn: (String) -> String) =
        fn(
                try {
                    try {
                        zap(string)
                    }
                    catch (e: Exception) { "" }
                } catch (e: Exception) { "" }
        )

fun box(): String = tryZap("OK") { it }
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ Exception
