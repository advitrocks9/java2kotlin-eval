package edgecases.consts

object Sample {
    const val RETRY_LIMIT: Int = 3
    const val TIMEOUT_MS: Long = 5000L
    val BASE_PATH: String = "/api/v1"
    const val DEBUG: Boolean = false

    val COMPUTED: Int = 1 + 2
    val EXCLUDED: IntArray = intArrayOf(1, 2, 3)
}
