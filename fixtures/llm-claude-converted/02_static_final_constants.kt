package edgecases.consts

class Sample {
    companion object {
        const val RETRY_LIMIT: Int = 3
        const val TIMEOUT_MS: Long = 5000L
        const val BASE_PATH: String = "/api/v1"
        const val DEBUG: Boolean = false

        const val COMPUTED: Int = 1 + 2
        val EXCLUDED: IntArray = intArrayOf(1, 2, 3)
    }
}
