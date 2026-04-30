package edgecases.arrays

object Sample {
    fun words(): Array<String> = arrayOf("alpha", "beta", "gamma")

    fun grid(): Array<IntArray> {
        val g = Array(3) { IntArray(4) }
        for (i in 0 until 3) {
            for (j in 0 until 4) {
                g[i][j] = i * 4 + j
            }
        }
        return g
    }
}
