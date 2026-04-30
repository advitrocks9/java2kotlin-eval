package edgecases.checked

import java.io.IOException

class Sample {
    fun writeOut(s: String?) {
        if (s == null) throw IOException("null")
        println(s)
    }

    fun wrap() {
        try {
            writeOut("ok")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
