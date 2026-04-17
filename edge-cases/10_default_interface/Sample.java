// Hypothesis: Java interface default methods should map cleanly to Kotlin
// interface methods with bodies. The trap is when a class inherits from
// two interfaces that both provide a default with the same signature -
// Java forces an explicit override; Kotlin uses `super<I>` syntax. J2K
// rarely sees the diamond, but JCommander has multi-interface defaults.
package edgecases.defaultmethods;

public interface Sample {
    String name();

    default String greeting() {
        return "hello, " + name();
    }
}
