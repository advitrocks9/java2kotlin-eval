// Synthetic fixture mimicking J2K output for a Java class with public static
// final primitive/string fields. NJ2K does promote `private static final`
// (verified against testData/newJ2k/staticMembers/PrivateStaticMembers.kt),
// but the public case is the one I want to demonstrate the fix on -- it
// shows up in real codebases far more often.
//
// See docs/PROPOSED_FIX.md for the before/after I expect.
class Defaults {
    fun configured(): String = "$BASE_PATH:$PORT"

    companion object {
        val RETRY_LIMIT = 3
        val PORT = 8080
        val TIMEOUT_MS = 5000L
        val DEBUG = false
        val BASE_PATH = "/api/v1"
        // computed RHS - my fix is conservative here, leaves it as `val`
        val COMPUTED = 1 + 2
    }
}
