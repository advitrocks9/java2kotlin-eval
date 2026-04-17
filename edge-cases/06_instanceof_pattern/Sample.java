// Hypothesis: Java 17 instanceof-with-binding (`x instanceof String s`)
// should become Kotlin smart-cast `if (x is String) ... x ...`. The trap is
// that the Java pattern lets you USE `s` outside the if branch when the
// compiler can prove flow definite-assignment. Kotlin's smart cast scope is
// purely lexical. J2K should refuse the rewrite or insert an explicit `as`.
package edgecases.instanceofpat;

public class Sample {
    public String describe(Object o) {
        if (o instanceof String s) {
            return "str(" + s.length() + ")";
        }
        if (!(o instanceof Integer i)) {
            return "other";
        }
        // `i` is in scope here in Java, NOT in Kotlin's smart cast model.
        return "int(" + (i + 1) + ")";
    }
}
