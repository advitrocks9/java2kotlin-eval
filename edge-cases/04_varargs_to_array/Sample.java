// Hypothesis: Java varargs `String...` should become Kotlin `vararg s: String`,
// but only at the parameter site. When the same method is called with an
// explicit `new String[]{}`, J2K has to translate that to `*arrayOf(...)` at
// the call site -- this is the failure I expect to see if there are internal
// callers passing arrays.
package edgecases.varargs;

public class Sample {
    public static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public static String demo() {
        // expected: this call should become `join(",", *arrayOf("a","b"))` or
        // `join(",", "a", "b")`. The array form is the J2K stress.
        String[] parts = new String[]{"a", "b"};
        return join(",", parts);
    }
}
