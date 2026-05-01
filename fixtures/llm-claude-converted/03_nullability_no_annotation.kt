package edgecases.nullability

import java.util.HashMap

class Sample {
    private val store: MutableMap<String, String> = HashMap()

    fun maybeGet(key: String): String? = store[key]

    fun findOrThrow(key: String): String {
        val v = store[key] ?: throw IllegalStateException("missing $key")
        return v
    }
}
