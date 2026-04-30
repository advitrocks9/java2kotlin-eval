package edgecases.utility

object Sample {
    fun clamp(v: Int, lo: Int, hi: Int): Int = Math.max(lo, Math.min(v, hi))

    fun sign(v: Int): Int = Integer.compare(v, 0)
}
