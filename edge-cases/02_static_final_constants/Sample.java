// Hypothesis: J2K converts `static final int FOO = 7` to `val FOO = 7`
// inside a `companion object`, but it should produce `const val FOO = 7`
// because the RHS is a primitive literal and the symbol is JVM-final.
// Without `const`, callers can't use it in annotations, and we lose
// `inline`-ability for downstream consumers.
package edgecases.consts;

public class Sample {
    public static final int RETRY_LIMIT = 3;
    public static final long TIMEOUT_MS = 5000L;
    public static final String BASE_PATH = "/api/v1";
    public static final boolean DEBUG = false;

    public static final int COMPUTED = 1 + 2; // expected: const-eligible (compile-time)
    public static final int[] EXCLUDED = {1, 2, 3}; // expected: NOT const (array)
}
