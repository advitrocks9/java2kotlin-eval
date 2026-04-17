// Hypothesis: a non-static inner class implicitly captures the enclosing
// `Outer.this`. Kotlin does this with `inner class`. J2K must add the
// `inner` keyword - it can't make it `class` because that would lose
// access to the outer instance. I expect this case to come out correct
// (J2K is mature here), but worth confirming.
package edgecases.inner;

public class Sample {
    private final String tag;

    public Sample(String tag) { this.tag = tag; }

    public class Child {
        public String label() {
            return tag + "/child"; // captures outer `tag`
        }
    }

    // Static nested - should NOT become `inner` in Kotlin
    public static class StaticChild {
        public String label() { return "static/child"; }
    }
}
