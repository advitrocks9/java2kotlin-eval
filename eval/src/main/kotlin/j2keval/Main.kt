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
 * If a sibling `expectations.json` exists, also runs the per-case hypothesis
 * regex checks and reports pass/fail by hypothesis.
 *
 * Usage:
 *   ./gradlew :eval:run --args="<kt-dir> [<report-out>] [--expectations=<json>]"
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
    val compileResults = ktFiles.map { compile.check(it) }

    val structural = ktFiles.map { Metrics.scan(it) }
    val psiEnv = PsiEnv()
    val psi = ktFiles.map { f -> PsiScan.scan(f, psiEnv.parse(f)) }
    psiEnv.close()

    val expectations = parsed.expectations?.let { loadExpectations(it) } ?: emptyMap()
    val hypothesisResults: Map<String, HypothesisCheck> = ktFiles.mapNotNull { p ->
        val key = ktDir.relativize(p).toString()
        val expected = expectations[key] ?: return@mapNotNull null
        key to Metrics.checkHypothesis(p, expected)
    }.toMap()

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

    val passRate = compileResults.count { it.ok }.toDouble() / compileResults.size
    println("[eval] kotlinc pass rate: ${"%.1f".format(passRate * 100)}% (${compileResults.count { it.ok }}/${compileResults.size})")
}

private data class Args(val ktDir: Path, val report: Path?, val expectations: Path?)

private fun parseArgs(args: List<String>): Args {
    if (args.isEmpty()) {
        System.err.println("usage: j2keval <kt-dir> [<report-out>] [--expectations=<json>]")
        exitProcess(2)
    }
    var report: Path? = null
    var expectations: Path? = null
    val positional = mutableListOf<String>()
    for (a in args) {
        when {
            a.startsWith("--expectations=") -> expectations = Path.of(a.removePrefix("--expectations="))
            a.startsWith("--") -> { System.err.println("unknown flag: $a"); exitProcess(2) }
            else -> positional += a
        }
    }
    if (positional.isEmpty()) {
        System.err.println("usage: j2keval <kt-dir> [<report-out>] [--expectations=<json>]")
        exitProcess(2)
    }
    val ktDir = Path.of(positional[0])
    if (positional.size >= 2) report = Path.of(positional[1])
    return Args(ktDir, report, expectations)
}
