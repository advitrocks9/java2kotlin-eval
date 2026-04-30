package j2keval

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess

/**
 * eval entry point. given a directory of .kt files (J2K output), produces a
 * markdown report with:
 *   - per-file kotlinc result
 *   - aggregate compile rate
 *   - structural metrics (platform-type !! count, anonymous-object count,
 *     `val` -> `const val` candidates)
 *   - error bucket histogram
 *
 * Optionally pass `--expectations=path/to/expectations.txt` to run the
 * per-case hypothesis regex checks and report pass/fail by hypothesis.
 * The format is one line per case: `relpath | tag | yes|no | regex | description`.
 *
 * Usage:
 *   ./gradlew :eval:run --args="<kt-dir> [<report-out>] [--expectations=<file>]"
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "fix-const-val") {
        if (args.size < 2) {
            System.err.println("usage: j2keval fix-const-val <kt-dir>")
            exitProcess(2)
        }
        val n = runConstValFix(Path.of(args[1]))
        exitProcess(if (n > 0) 0 else 0)
    }

    val parsed = parseArgs(args.toList())
    val ktDir = parsed.ktDir
    if (!ktDir.toFile().exists()) {
        System.err.println("[eval] kt dir does not exist: ${ktDir.absolute()}")
        exitProcess(2)
    }

    println("[eval] scanning $ktDir")
    val ktFiles = collectKotlinFiles(ktDir)
    if (ktFiles.isEmpty()) {
        System.err.println("[eval] no .kt files found under $ktDir")
        exitProcess(1)
    }
    println("[eval] found ${ktFiles.size} .kt files")

    val compile = CompileChecker(System.getenv("KOTLINC") ?: "kotlinc")
    val compileResults = compile.checkAll(ktFiles, parsed.compileMode)

    val structural = ktFiles.map { Metrics.scan(it) }
    val psiEnv = PsiEnv()
    val psi = ktFiles.map { f -> PsiScan.scan(f, psiEnv.parse(f)) }
    psiEnv.close()

    val expectations = parsed.expectations?.let { loadExpectations(it) } ?: emptyMap()
    val hypothesisResults: List<Pair<String, HypothesisCheck>> = ktFiles.flatMap { p ->
        val key = ktDir.relativize(p).toString()
        val expected = expectations[key].orEmpty()
        expected.map { e -> key to Metrics.checkHypothesis(p, e) }
    }

    val report = Report.render(
        ktDir = ktDir,
        compile = compileResults,
        structural = structural,
        psi = psi,
        hypotheses = hypothesisResults,
    )

    val outFile = parsed.report
    if (outFile != null) {
        outFile.toFile().parentFile?.mkdirs()
        outFile.toFile().writeText(report)
        println("[eval] wrote report to $outFile")
    } else {
        println()
        println(report)
    }

    val passed = compileResults.count { it.ok }
    val failed = compileResults.size - passed
    val passRate = passed.toDouble() / compileResults.size
    println("[eval] kotlinc pass rate: ${"%.1f".format(passRate * 100)}% ($passed/${compileResults.size})")

    val hypothesisFails = hypothesisResults.count { !it.second.passed }
    var exitCode = 0
    if (failed > parsed.allowCompileFails) {
        System.err.println("[eval] $failed compile failure(s) exceeds --allow-compile-fails=${parsed.allowCompileFails}")
        exitCode = 1
    }
    if (hypothesisFails > 0) {
        System.err.println("[eval] $hypothesisFails hypothesis check(s) failed -- see report")
        if (exitCode == 0) exitCode = 3
    }
    if (exitCode != 0) exitProcess(exitCode)
}

private data class Args(
    val ktDir: Path,
    val report: Path?,
    val expectations: Path?,
    val compileMode: CompileMode,
    val allowCompileFails: Int,
)

private fun parseArgs(args: List<String>): Args {
    val usage = "usage: j2keval <kt-dir> [<report-out>] [--expectations=<file>] [--isolated|--module] [--allow-compile-fails=<N>]"
    if (args.isEmpty()) { System.err.println(usage); exitProcess(2) }

    var report: Path? = null
    var expectations: Path? = null
    var mode: CompileMode = CompileMode.MODULE
    var allowCompileFails = 0
    val positional = mutableListOf<String>()
    for (a in args) {
        when {
            a.startsWith("--expectations=") -> expectations = Path.of(a.removePrefix("--expectations="))
            a == "--isolated" -> mode = CompileMode.ISOLATED
            a == "--module" -> mode = CompileMode.MODULE
            a.startsWith("--allow-compile-fails=") ->
                allowCompileFails = a.removePrefix("--allow-compile-fails=").toIntOrNull()
                    ?: run { System.err.println("invalid int in $a"); exitProcess(2) }
            a.startsWith("--") -> { System.err.println("unknown flag: $a\n$usage"); exitProcess(2) }
            else -> positional += a
        }
    }
    if (positional.isEmpty()) { System.err.println(usage); exitProcess(2) }
    val ktDir = Path.of(positional[0])
    if (positional.size >= 2) report = Path.of(positional[1])
    return Args(ktDir, report, expectations, mode, allowCompileFails)
}
