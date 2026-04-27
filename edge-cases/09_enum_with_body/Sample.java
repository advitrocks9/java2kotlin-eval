// Hypothesis: enum constants with overridden methods (the "fancy enum" form)
// should map to a Kotlin enum with abstract method + per-constant override.
// J2K usually does this. The stress is that one constant overrides while
// another doesn't - Java permits this if the enum class itself is non-abstract
// and provides a default. Kotlin requires abstract OR none.
package edgecases.enumbody;

public enum Sample {
    PLUS {
        @Override
        public int apply(int a, int b) { return a + b; }
    },
    MINUS {
        @Override
        public int apply(int a, int b) { return a - b; }
    },
    NOOP; // no override - inherits the default below

    public int apply(int a, int b) {
        return a;
    }
}
