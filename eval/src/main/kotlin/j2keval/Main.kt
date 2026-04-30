package j2keval

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess

// eval entry point. takes a directory of .kt files, produces a markdown
// report and a sibling .jsonl with: per-file kotlinc result, aggregate
// compile rate, structural metrics (regex + PSI), error bucket histogram,
// hypothesis pass/fail when --expectations= is set.
//
// --source=<name> tags the JSONL records so two runs on the same corpus
// (j2k vs claude vs gpt-5 vs whatever) can be joined and compared. default
// "j2k". --jsonl=<path> overrides the default sibling-of-report path.
//
// usage:
//   ./gradlew :eval:run --args="<kt-dir> [<report-out>] \
//       [--expectations=<file>] [--isolated|--module] \
//       [--allow-compile-fails=<N>] [--source=<name>] [--jsonl=<path>]"
fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "fix-const-val") {
        if (args.size < 2) {
            System.err.println("usage: j2keval fix-const-val <kt-dir>")
            exitProcess(2)
        }
        val n = runConstValFix(Path.of(args[1]))
        exitProcess(if (n > 0) 0 else 0)
    }
    if (args.isNotEmpty() && args[0] == "compare") {
        if (args.size < 3) {
            System.err.println("usage: j2keval compare <a.jsonl> <b.jsonl> [<out.md>]")
            exitProcess(2)
        }
        val out = if (args.size >= 4) Path.of(args[3]) else null
        Compare.run(Path.of(args[1]), Path.of(args[2]), out)
        exitProcess(0)
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

    val baselineComparisons: List<BaselineComparison> = parsed.baselineCorpus?.let { baseRoot ->
        if (!baseRoot.toFile().exists()) {
            System.err.println("[eval] baseline corpus not found: $baseRoot")
            exitProcess(2)
        }
        BaselineDiff.compareCorpus(ktDir, baseRoot)
    }.orEmpty()

    // Pair every .kt with its sibling .java if one exists; scan the .java
    // for input-side counts so the report can answer "did J2K miss any".
    val javaScans: Map<Path, JavaMetrics> = ktFiles.mapNotNull { kt ->
        val javaSibling = ktDir.resolve(ktDir.relativize(kt).toString().removeSuffix(".kt") + ".java")
        if (!javaSibling.toFile().exists()) null else javaSibling.let {
            JavaScan.scan(it)?.let { jm -> kt to jm }
        }
    }.toMap()

    val report = Report.render(
        ktDir = ktDir,
        compile = compileResults,
        structural = structural,
        psi = psi,
        hypotheses = hypothesisResults,
        baselineComparisons = baselineComparisons,
        javaScans = javaScans,
        ktFiles = ktFiles,
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

    val jsonlPath = parsed.jsonl ?: outFile?.let {
        Path.of(it.toString().removeSuffix(".md") + ".jsonl")
    }
    if (jsonlPath != null) {
        val samples = buildSamples(
            ktDir = ktDir,
            ktFiles = ktFiles,
            corpus = ktDir.toString(),
            source = parsed.source,
            compile = compileResults,
            structural = structural,
            psi = psi,
            hypotheses = hypothesisResults,
            expectations = expectations,
            baselineComparisons = baselineComparisons,
            javaScans = javaScans,
        )
        Jsonl.write(jsonlPath, samples)
        println("[eval] wrote ${samples.size} JSONL records to $jsonlPath")
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
    val source: String,
    val jsonl: Path?,
    val baselineCorpus: Path?,
)

private fun parseArgs(args: List<String>): Args {
    val usage = "usage: j2keval <kt-dir> [<report-out>] [--expectations=<file>] [--isolated|--module] [--allow-compile-fails=<N>] [--source=<name>] [--jsonl=<path>] [--baseline-corpus=<path>]"
    if (args.isEmpty()) { System.err.println(usage); exitProcess(2) }

    var report: Path? = null
    var expectations: Path? = null
    var mode: CompileMode = CompileMode.MODULE
    var allowCompileFails = 0
    var source = "j2k"
    var jsonl: Path? = null
    var baselineCorpus: Path? = null
    val positional = mutableListOf<String>()
    for (a in args) {
        when {
            a.startsWith("--expectations=") -> expectations = Path.of(a.removePrefix("--expectations="))
            a == "--isolated" -> mode = CompileMode.ISOLATED
            a == "--module" -> mode = CompileMode.MODULE
            a.startsWith("--allow-compile-fails=") ->
                allowCompileFails = a.removePrefix("--allow-compile-fails=").toIntOrNull()
                    ?: run { System.err.println("invalid int in $a"); exitProcess(2) }
            a.startsWith("--source=") -> source = a.removePrefix("--source=")
            a.startsWith("--jsonl=") -> jsonl = Path.of(a.removePrefix("--jsonl="))
            a.startsWith("--baseline-corpus=") -> baselineCorpus = Path.of(a.removePrefix("--baseline-corpus="))
            a.startsWith("--") -> { System.err.println("unknown flag: $a\n$usage"); exitProcess(2) }
            else -> positional += a
        }
    }
    if (positional.isEmpty()) { System.err.println(usage); exitProcess(2) }
    val ktDir = Path.of(positional[0])
    if (positional.size >= 2) report = Path.of(positional[1])
    return Args(ktDir, report, expectations, mode, allowCompileFails, source, jsonl, baselineCorpus)
}

private fun buildSamples(
    ktDir: Path,
    ktFiles: List<Path>,
    corpus: String,
    source: String,
    compile: List<CompileResult>,
    structural: List<StructuralMetrics>,
    psi: List<PsiMetrics>,
    hypotheses: List<Pair<String, HypothesisCheck>>,
    expectations: Map<String, List<Expectation>>,
    baselineComparisons: List<BaselineComparison>,
    javaScans: Map<Path, JavaMetrics>,
): List<SampleResult> {
    val baselineByFile = baselineComparisons.associateBy { it.file }
    val byPath = ktFiles.associateWith { ktDir.relativize(it).toString() }
    val compileByPath = compile.associateBy { it.file }
    val structByPath = structural.associateBy { it.file }
    val psiByPath = psi.associateBy { it.file }
    val hypothesesByKey = hypotheses.groupBy({ it.first }, { it.second })

    return ktFiles.map { f ->
        val key = byPath.getValue(f)
        val cr = compileByPath.getValue(f)
        val sm = structByPath.getValue(f)
        val pm = psiByPath[f]
        val hs = hypothesesByKey[key].orEmpty()
        val expects = expectations[key].orEmpty()
        val javaSibling = ktDir.resolve(key.removeSuffix(".kt") + ".java")
        SampleResult(
            corpus = corpus,
            source = source,
            file = key,
            javaInput = if (javaSibling.toFile().exists()) ktDir.relativize(javaSibling).toString() else null,
            compile = CompileBlock(cr.ok, cr.errors, cr.durationMs),
            metricsRegex = MetricsRegexBlock(
                loc = sm.locKotlin,
                notNullAsserts = sm.notNullAsserts,
                anonObjects = sm.anonymousObjects,
                funInterface = sm.funInterface,
                constVal = sm.constVal,
                plainVal = sm.plainVal,
                constEligibleVal = sm.constEligibleVal,
                throwsAnnotations = sm.javaThrowsAnnotations,
                innerClass = sm.innerClass,
                vararg_ = sm.varargParams,
                useBlocks = sm.useBlocks,
            ),
            metricsPsi = pm?.let {
                MetricsPsiBlock(
                    loc = it.locKotlin,
                    notNullAsserts = it.notNullAsserts,
                    objectLiteralExprs = it.objectLiteralExprs,
                    funInterfaces = it.funInterfaces,
                    constVals = it.constVals,
                    plainVals = it.plainVals,
                    constEligibleVals = it.constEligibleVals,
                    innerClasses = it.innerClasses,
                    varargParams = it.varargParams,
                )
            },
            metricsJava = javaScans[f]?.let {
                MetricsJavaBlock(
                    loc = it.locJava,
                    parseFailed = it.parseFailed,
                    tryWithResourceCount = it.tryWithResourceCount,
                    resourceCount = it.resourceCount,
                    anonymousClassExprs = it.anonymousClassExprs,
                    staticFinalFields = it.staticFinalFields,
                    staticFinalLiteralFields = it.staticFinalLiteralFields,
                    varargParameters = it.varargParameters,
                    innerClassDecls = it.innerClassDecls,
                    singleAbstractMethodInterfaces = it.singleAbstractMethodInterfaces,
                )
            },
            hypotheses = hs.zip(expects) { check, exp ->
                HypothesisBlock(
                    tag = check.tag,
                    passed = check.passed,
                    shouldMatch = exp.shouldMatch,
                    pattern = exp.pattern,
                    expectation = check.expectation,
                    sample = check.actualSnippet,
                )
            },
            baseline = baselineByFile[key]?.let {
                BaselineBlock(
                    identical = it.identical,
                    deltaCount = it.deltaCount,
                    baselineMissing = it.baselineMissing,
                    unifiedDiff = it.unifiedDiff,
                )
            },
        )
    }
}
