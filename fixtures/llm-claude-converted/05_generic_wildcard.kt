package edgecases.variance

fun copy(src: List<out Number>, dst: MutableList<in Number>) {
    for (n in src) {
        dst.add(n)
    }
}
