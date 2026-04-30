package edgecases.utility

object Sample {
    fun clamp(v: Int, lo: Int, hi: Int): Int {
        return Math.max(lo, Math.min(v, hi))
    }

    fun sign(v: Int): Int {
        return Integer.compare(v, 0)
    }
}
