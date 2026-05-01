package edgecases.anon

class Sample {
    private val hook = Runnable { println("hook fired") }

    fun get(): Runnable = hook
}
