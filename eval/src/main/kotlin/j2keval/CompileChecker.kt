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

/**
 * Per-file kotlinc subprocess. Output goes to a throwaway tempdir (we don't
 * care about classfiles). Errors are filtered on `error:` lines, ignoring
 * the diagnostic about the output directory itself.
 *
 * One file at a time is slower than batching but keeps blame at a per-file
 * granularity. With 30 files * ~3s, 90s total -- fine for CI.
 */
class CompileChecker(private val kotlincBin: String) {
    private val tmp: Path = Files.createTempDirectory("j2k-eval-")

    fun check(file: Path): CompileResult {
        val start = System.nanoTime()
        val proc = ProcessBuilder(
            kotlincBin,
            file.toString(),
            "-d", tmp.toString(),
        )
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        val exited = proc.waitFor(60, TimeUnit.SECONDS)
        val tookMs = (System.nanoTime() - start) / 1_000_000
        if (!exited) {
            proc.destroyForcibly()
            return CompileResult(file, false, listOf("timeout after 60s"), tookMs)
        }
        val errors = output.lineSequence()
            .filter { it.contains(": error:") }
            .filterNot { "directory not found" in it }
            .toList()
        val ok = proc.exitValue() == 0 && errors.isEmpty()
        return CompileResult(file, ok, errors, tookMs)
    }
}
