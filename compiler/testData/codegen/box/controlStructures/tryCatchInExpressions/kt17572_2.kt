fun zap(s: String) = s

inline fun tryZap(s1: String, s2: String, fn: (String, String) -> String) =
        fn(
                try { zap(s1) } catch (e: Exception) { "" },
                try { zap(s2) } catch (e: Exception) { "" }
        )

fun box(): String = tryZap("O", "K") { a, b -> a + b }
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ Exception
