package edgecases.enumbody

enum class Sample {
    PLUS {
        override fun apply(a: Int, b: Int): Int = a + b
    },
    MINUS {
        override fun apply(a: Int, b: Int): Int = a - b
    },
    NOOP;

    open fun apply(a: Int, b: Int): Int = a
}
