package edgecases.overloads

class Sample {
    fun render(s: String): String = render(s, "", false)

    fun render(s: String, prefix: String): String = render(s, prefix, false)

    fun render(s: String, prefix: String, upper: Boolean): String {
        val r = prefix + s
        return if (upper) r.uppercase() else r
    }
}
