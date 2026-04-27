package edgecases.twr

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

class Sample {
    @Throws(IOException::class)
    fun readOne(path: String?): String? {
        BufferedReader(FileReader(path)).use { r ->
            return r.readLine()
        }
    }

    @Throws(IOException::class)
    fun readTwo(pathA: String?, pathB: String?): String? {
        BufferedReader(FileReader(pathA)).use { a ->
            BufferedReader(FileReader(pathB)).use { b ->
                return a.readLine() + b.readLine()
            }
        }
    }
}
