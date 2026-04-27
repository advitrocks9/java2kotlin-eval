package j2keval

import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Structural metrics over the J2K Kotlin output. Deliberately syntactic --
 * I prefer regex on raw .kt text over wiring up KotlinCoreEnvironment +
 * Disposable, which is non-trivial outside a test framework. The metrics here
 * are stable enough to be useful: they answer questions like "did J2K leave
 * anonymous-object expressions where a SAM lambda would do?" by counting
 * occurrences, not by full type analysis.
 *
 * Limits called out explicitly in the writeup:
 *   - regex doesn't track scoping. `!!` inside a string literal would be
 *     counted (in practice, doesn't happen often in J2K output)
 *   - "const-eligible" is determined by RHS syntactic shape, not full
 *     compile-time-constant analysis. JVM compile-time-constant evaluation
 *     would need a real type-checker.
 */
data class StructuralMetrics(
    val file: Path,
    val locKotlin: Int,
    val notNullAsserts: Int,           // !! occurrences
    val anonymousObjects: Int,         // `object :` (literal anon-class form)
    val funInterface: Int,             // `fun interface ...`
    val constVal: Int,
    val plainVal: Int,
    val constEligibleVal: Int,         // val whose RHS is a primitive/string literal at top-level / companion
    val javaThrowsAnnotations: Int,    // @Throws(...) -- could be over-annotation
    val innerClass: Int,               // `inner class`
    val varargParams: Int,             // `vararg `
    val useBlocks: Int,                // try-with-resources -> .use { }
    val platformTypeHints: Int,        // " : T! ..." -- IntelliJ doesn't actually emit `!`, but we look for the kdoc convention `// platform type` it inserts
)

data class HypothesisCheck(
    val tag: String,
    val passed: Boolean,
    val expectation: String,
    val actualSnippet: String?,
)

/**
 * For each Kotlin file produced by J2K, score it.
 */
object Metrics {
    private val notNullAssert = Regex("""!!""")
    private val anonObject = Regex("""\bobject\s*:""")
    private val funInterface = Regex("""\bfun\s+interface\b""")
    private val constVal = Regex("""\bconst\s+val\b""")
    private val plainVal = Regex("""(?<!const\s)\bval\b""")
    private val innerClass = Regex("""\binner\s+class\b""")
    private val vararg_ = Regex("""\bvararg\s+\w""")
    private val useBlock = Regex("""\.use\s*\{""")
    private val throwsAnno = Regex("""@Throws\s*\(""")
    private val platformHint = Regex("""\s*//\s*platform type""")

    // RHS of a `val NAME = LITERAL` where LITERAL is a primitive or string literal.
    private val constEligibleVal = Regex(
        """\bval\s+\w+\s*(?::\s*[\w<>?]+)?\s*=\s*(?:"[^"]*"|-?\d[\d_]*[LfFdD]?|true|false|'[^']'|\d*\.\d+[fFdD]?)\s*$""",
        RegexOption.MULTILINE
    )

    fun scan(file: Path): StructuralMetrics {
        val text = file.readText()
        val loc = text.count { it == '\n' } + 1
        val constMatches = constVal.findAll(text).count()
        val plainMatches = plainVal.findAll(text).count()
        val constEligible = constEligibleVal.findAll(text)
            .filter { match ->
                // exclude the ones already declared `const` -- look back ~10 chars for `const`
                val start = match.range.first
                val window = text.substring((start - 12).coerceAtLeast(0), start)
                "const" !in window
            }
            .count()
        return StructuralMetrics(
            file = file,
            locKotlin = loc,
            notNullAsserts = notNullAssert.findAll(text).count(),
            anonymousObjects = anonObject.findAll(text).count(),
            funInterface = funInterface.findAll(text).count(),
            constVal = constMatches,
            plainVal = plainMatches,
            constEligibleVal = constEligible,
            javaThrowsAnnotations = throwsAnno.findAll(text).count(),
            innerClass = innerClass.findAll(text).count(),
            varargParams = vararg_.findAll(text).count(),
            useBlocks = useBlock.findAll(text).count(),
            platformTypeHints = platformHint.findAll(text).count(),
        )
    }

    fun checkHypothesis(file: Path, expectation: Expectation): HypothesisCheck {
        val text = file.readText()
        val pattern = Regex(expectation.pattern, setOf(RegexOption.MULTILINE))
        val match = pattern.find(text)
        val passed = when (expectation.shouldMatch) {
            true -> match != null
            false -> match == null
        }
        val snippet = match?.value?.take(120)
        return HypothesisCheck(
            tag = expectation.tag,
            passed = passed,
            expectation = expectation.description,
            actualSnippet = snippet,
        )
    }
}

data class Expectation(
    val tag: String,
    val description: String,
    val pattern: String,
    val shouldMatch: Boolean,
)
