package edgecases.inner

class Sample(private val tag: String) {

    inner class Child {
        fun label(): String = "$tag/child"
    }

    class StaticChild {
        fun label(): String = "static/child"
    }
}
