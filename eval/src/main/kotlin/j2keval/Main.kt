package j2keval

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: j2keval <kotlin-dir> [report-output]")
        exitProcess(2)
    }
    println("eval scaffold ok, args=${args.toList()}")
}
