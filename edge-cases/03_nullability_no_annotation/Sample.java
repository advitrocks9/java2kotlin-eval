// Hypothesis: with no @Nullable / @NotNull, J2K has to guess. For a field set
// in the ctor, J2K usually picks non-null; for a method that returns a Map.get()
// result, J2K should leave it nullable. The trap is the `findOrThrow` shape
// where the Java code throws on null but the type signature looks nullable.
// Expect J2K to over-report nullable here.
package edgecases.nullability;

import java.util.HashMap;
import java.util.Map;

public class Sample {
    private final Map<String, String> store = new HashMap<>();

    public String maybeGet(String key) {
        return store.get(key); // nullable -> Kotlin should infer String?
    }

    public String findOrThrow(String key) {
        String v = store.get(key);
        if (v == null) throw new IllegalStateException("missing " + key);
        return v; // never null at runtime, but J2K can't see that
    }
}
