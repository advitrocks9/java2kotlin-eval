package edgecases.anon

class Sample {
    private val hook: Runnable = object : Runnable {
        override fun run() {
            println("hook fired")
        }
    }

    fun get(): Runnable? {
        return hook
    }
}
