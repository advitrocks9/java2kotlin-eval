// Hypothesis: Java's `List<? extends Number>` should map to Kotlin
// `List<out Number>` (declaration-site is variance-safe). The `super` form
// goes to `in Number`. J2K usually gets one direction right but stumbles
// when both appear in the same signature, especially with intersection types.
package edgecases.variance;

import java.util.List;

public class Sample {
    // expected: fun copy(src: List<out Number>, dst: MutableList<in Number>)
    public static void copy(List<? extends Number> src, List<? super Number> dst) {
        for (Number n : src) {
            dst.add(n);
        }
    }
}
