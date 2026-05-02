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

class CompileChecker(
    private val kotlincBin: String,
    /**
     * Extra classpath roots to forward to kotlinc. Defaults to `target/.kotlin-classpath.txt`
     * if present, otherwise empty. Format is one path per line.
     */
    private val extraClasspath: List<String> = readClasspathFile(),
    /**
     * Target JVM bytecode level. Matches the runner module + the IntelliJ Platform
     * minimum at the time of writing.
     */
    private val jvmTarget: String = "17",
) {

    fun checkAll(files: List<Path>, mode: CompileMode): List<CompileResult> = when (mode) {
        CompileMode.MODULE -> checkModule(files)
        CompileMode.ISOLATED -> files.map { checkOne(it) }
    }

    private fun baseArgs(): List<String> {
        val cp = extraClasspath.joinToString(java.io.File.pathSeparator)
        return buildList {
            if (cp.isNotEmpty()) { add("-classpath"); add(cp) }
            add("-jvm-target"); add(jvmTarget)
        }
    }

    private fun checkModule(files: List<Path>): List<CompileResult> {
        if (files.isEmpty()) return emptyList()
        val tmp = Files.createTempDirectory("j2k-eval-")
        val start = System.nanoTime()
        val args = buildList {
            add(kotlincBin)
            addAll(baseArgs())
            add("-d"); add(tmp.toString())
            addAll(files.map { it.toString() })
        }
        // capture stdout and stderr separately. previously this used
        // redirectErrorStream which made the "is stderr empty" check
        // impossible. now: stdout shows progress chatter, stderr is the
        // diagnostic stream, and exit code 0 means kotlinc accepted the
        // input. all three must agree before we call it a pass.
        // drain both streams in parallel -- kotlinc fills the stderr buffer
        // (~64KB) on a many-file batch and blocks on write if no one is
        // reading. sequential readText() deadlocks: stdout drained, stderr
        // pipe full, kotlinc stuck waiting to flush.
        val proc = ProcessBuilder(args).start()
        val (stdout, stderr) = drainStreams(proc)
        val exited = proc.waitFor(5, TimeUnit.MINUTES)
        val totalMs = (System.nanoTime() - start) / 1_000_000
        if (!exited) {
            proc.destroyForcibly()
            return files.map { CompileResult(it, false, listOf("kotlinc batch timeout after 5min"), totalMs) }
        }
        val exitCode = proc.exitValue()

        // kotlinc emits `<path>:<line>:<col>: error: ...` on stderr. group by
        // file path so we can attribute failures.
        val combined = (stdout + "\n" + stderr).lineSequence()
        val errsByFile: Map<Path, List<String>> = combined
            .filter { it.contains(": error:") }
            .filterNot { "directory not found" in it }
            .mapNotNull { line ->
                val m = Regex("""^(.+?):\d+:\d+: error: """).find(line) ?: return@mapNotNull null
                Path.of(m.groupValues[1]).toAbsolutePath().normalize() to line
            }
            .groupBy({ it.first }, { it.second })

        // any stderr line that wasn't successfully attributed to a file is a
        // batch-level failure (unresolved options, missing classpath, etc).
        // surface it on every input file so the report doesn't lie.
        val unattributedStderr = stderr.lineSequence()
            .filter { it.isNotBlank() && !it.contains(": error:") && (it.contains("error:") || it.contains("Error:")) }
            .toList()

        val perFileMs = totalMs / files.size.coerceAtLeast(1)
        return files.map { f ->
            val attributedErrs = errsByFile[f.toAbsolutePath().normalize()].orEmpty()
            val ok = exitCode == 0 && attributedErrs.isEmpty() && unattributedStderr.isEmpty()
            val errs = when {
                attributedErrs.isNotEmpty() -> attributedErrs
                exitCode != 0 && unattributedStderr.isNotEmpty() -> unattributedStderr
                exitCode != 0 -> listOf("kotlinc exit=$exitCode (no per-file diagnostic)")
                else -> emptyList()
            }
            CompileResult(f, ok, errs, perFileMs)
        }
    }

    private fun checkOne(file: Path): CompileResult {
        val tmp = Files.createTempDirectory("j2k-eval-iso-")
        val start = System.nanoTime()
        val args = buildList {
            add(kotlincBin)
            addAll(baseArgs())
            add(file.toString())
            add("-d"); add(tmp.toString())
        }
        val proc = ProcessBuilder(args).start()
        val (stdout, stderr) = drainStreams(proc)
        val exited = proc.waitFor(60, TimeUnit.SECONDS)
        val tookMs = (System.nanoTime() - start) / 1_000_000
        if (!exited) {
            proc.destroyForcibly()
            return CompileResult(file, false, listOf("timeout after 60s"), tookMs)
        }
        val exitCode = proc.exitValue()
        val errs = (stdout + "\n" + stderr).lineSequence()
            .filter { it.contains(": error:") }
            .filterNot { "directory not found" in it }
            .toList()
        // tighter check than the original: exit==0 AND stderr clean of error lines
        // (warnings on stderr are fine; kotlinc prefixes those with "warning:").
        val stderrErrors = stderr.lineSequence()
            .filter { it.isNotBlank() && (it.contains("error:") || it.contains("Error:")) }
            .toList()
        val ok = exitCode == 0 && stderrErrors.isEmpty()
        val finalErrs = when {
            errs.isNotEmpty() -> errs
            !ok && stderrErrors.isNotEmpty() -> stderrErrors
            !ok -> listOf("kotlinc exit=$exitCode")
            else -> emptyList()
        }
        return CompileResult(file, ok, finalErrs, tookMs)
    }
}

/**
 * Reads `target/.kotlin-classpath.txt` if present, one absolute jar path per
 * line. Returns an empty list otherwise -- a fresh checkout that hasn't run
 * the runner's classpath dump yet still works for stdlib-only fixtures.
 *
 * The KOTLINC_CLASSPATH env var overrides the file (':'-separated, like a
 * normal classpath). Per-corpus eval runs pass it to scope kotlinc to the
 * deps the corpus actually needs (e.g. JCommander's testng + jackson).
 */
private fun readClasspathFile(): List<String> {
    val env = System.getenv("KOTLINC_CLASSPATH")?.trim()
    if (!env.isNullOrEmpty()) {
        return env.split(java.io.File.pathSeparatorChar).filter { it.isNotBlank() }
    }
    val candidate = Path.of("target", ".kotlin-classpath.txt")
    if (!Files.exists(candidate)) return emptyList()
    return Files.readAllLines(candidate).filter { it.isNotBlank() && !it.startsWith("#") }
}

/**
 * Drain stdout + stderr concurrently into strings. Java's Process inherits a
 * fixed-size pipe buffer per stream; if you read one to completion before
 * touching the other, a child that fills the un-read pipe blocks forever.
 * Using two threads with a small `Phaser`-equivalent (just `.join()`) is the
 * stdlib-recommended pattern.
 */
private fun drainStreams(proc: Process): Pair<String, String> {
    val outRef = java.util.concurrent.atomic.AtomicReference<String>("")
    val errRef = java.util.concurrent.atomic.AtomicReference<String>("")
    val outT = Thread { outRef.set(proc.inputStream.bufferedReader().readText()) }
    val errT = Thread { errRef.set(proc.errorStream.bufferedReader().readText()) }
    outT.isDaemon = true; errT.isDaemon = true
    outT.start(); errT.start()
    outT.join(); errT.join()
    return outRef.get() to errRef.get()
}
