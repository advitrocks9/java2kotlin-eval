package edgecases.varargs

object Sample {
    fun join(sep: String, vararg parts: String): String {
        val sb = StringBuilder()
        for (i in parts.indices) {
            if (i > 0) sb.append(sep)
            sb.append(parts[i])
        }
        return sb.toString()
    }

    fun demo(): String {
        val parts = arrayOf("a", "b")
        return join(",", *parts)
    }
}
