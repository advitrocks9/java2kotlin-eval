package edgecases.defaultmethods

interface Sample {
    fun name(): String

    fun greeting(): String = "hello, " + name()
}
