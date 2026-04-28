package j2keval

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class CompileResult(
    val file: Path,
    val ok: Boolean,
    val errors: List<String>,
    val durationMs: Long,
)

enum class CompileMode {
    /**
     * One kotlinc invocation over the whole corpus. The right answer for
     * a real codebase where files cross-reference each other (JCommander's
     * `JCommander.kt` imports `Parameter`, `Parameterized`, etc).
     */
    MODULE,

    /**
     * One kotlinc invocation per file, in parallel by file. The right answer
     * for a fixture corpus where each file is meant to be a standalone test
     * case -- e.g. JetBrains' `newJ2k` testData has multiple files declaring
     * top-level `class C { ... }` because each pair is its own world. A
     * MODULE compile of those would explode with redeclaration errors that
     * say nothing about J2K.
     */
    ISOLATED,
}

class CompileChecker(private val kotlincBin: String) {

    fun checkAll(files: List<Path>, mode: CompileMode): List<CompileResult> = when (mode) {
        CompileMode.MODULE -> checkModule(files)
        CompileMode.ISOLATED -> files.map { checkOne(it) }
    }

    private fun checkModule(files: List<Path>): List<CompileResult> {
        if (files.isEmpty()) return emptyList()
        val tmp = Files.createTempDirectory("j2k-eval-")
        val start = System.nanoTime()
        val args = buildList {
            add(kotlincBin); add("-d"); add(tmp.toString())
            addAll(files.map { it.toString() })
        }
        val proc = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val exited = proc.waitFor(5, TimeUnit.MINUTES)
        val totalMs = (System.nanoTime() - start) / 1_000_000
        if (!exited) {
            proc.destroyForcibly()
            return files.map { CompileResult(it, false, listOf("kotlinc batch timeout after 5min"), totalMs) }
        }

        // kotlinc emits `<path>:<line>:<col>: error: ...`. group by file path.
        val errsByFile: Map<Path, List<String>> = output.lineSequence()
            .filter { it.contains(": error:") }
            .filterNot { "directory not found" in it }
            .mapNotNull { line ->
                val m = Regex("""^(.+?):\d+:\d+: error: """).find(line) ?: return@mapNotNull null
                Path.of(m.groupValues[1]).toAbsolutePath().normalize() to line
            }
            .groupBy({ it.first }, { it.second })

        val perFileMs = totalMs / files.size.coerceAtLeast(1)
        return files.map { f ->
            val errs = errsByFile[f.toAbsolutePath().normalize()].orEmpty()
            CompileResult(f, errs.isEmpty(), errs, perFileMs)
        }
    }

    private fun checkOne(file: Path): CompileResult {
        val tmp = Files.createTempDirectory("j2k-eval-iso-")
        val start = System.nanoTime()
        val proc = ProcessBuilder(kotlincBin, file.toString(), "-d", tmp.toString())
            .redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val exited = proc.waitFor(60, TimeUnit.SECONDS)
        val tookMs = (System.nanoTime() - start) / 1_000_000
        if (!exited) {
            proc.destroyForcibly()
            return CompileResult(file, false, listOf("timeout after 60s"), tookMs)
        }
        val errs = output.lineSequence()
            .filter { it.contains(": error:") }
            .filterNot { "directory not found" in it }
            .toList()
        return CompileResult(file, errs.isEmpty(), errs, tookMs)
    }
}
