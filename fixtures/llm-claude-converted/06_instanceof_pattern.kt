package edgecases.instanceofpat

class Sample {
    fun describe(o: Any?): String {
        if (o is String) {
            return "str(" + o.length + ")"
        }
        if (o !is Int) {
            return "other"
        }
        // Kotlin smart cast applies after the negative type check above
        return "int(" + (o + 1) + ")"
    }
}
