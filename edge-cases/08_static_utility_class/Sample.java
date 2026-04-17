// Hypothesis: Java idiom "private constructor + only static methods" is the
// utility-class pattern. Kotlin's idiom is a top-level `object` (or top-level
// functions). J2K should detect the pattern. I expect it to over-conserve
// here and produce a class with a `companion object` housing all the
// methods, which keeps Java-callable signatures stable but isn't idiomatic.
package edgecases.utility;

public final class Sample {
    private Sample() {} // not constructible

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    public static int sign(int v) {
        return Integer.compare(v, 0);
    }
}
