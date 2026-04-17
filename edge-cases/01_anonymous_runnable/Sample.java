// Hypothesis: J2K leaves an `object : Runnable { ... }` here, even though
// the body is a single SAM method. Idiomatic Kotlin would be a lambda.
// The new j2k (NJ2K) is supposed to detect this via FunctionalInterfacesConversion;
// the question is whether it triggers when the anonymous class is captured
// into a field rather than passed as an argument.
package edgecases.anon;

public class Sample {
    private final Runnable hook = new Runnable() {
        @Override
        public void run() {
            System.out.println("hook fired");
        }
    };

    public Runnable get() {
        return hook;
    }
}
