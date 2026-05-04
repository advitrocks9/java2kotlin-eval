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
    val lateinitVars: Int,             // `lateinit var` -- J2K's escape hatch for non-null fields it can't initialise
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
    // `lateinit var` -- escape hatch J2K (and humans) reach for when a
    // non-null field can't be initialised at construction. high counts on
    // converted Kotlin signal "the converter chose not to make this nullable
    // and is asking the runtime to back it up", which is the opposite of
    // good null-safety scoring. visibility modifiers can sit on either side
    // of the lateinit keyword in J2K output.
    private val lateinitVar = Regex("""\blateinit\s+(?:(?:private|internal|public|protected)\s+)?var\b""")

    // RHS of a `val NAME = LITERAL` where LITERAL is a primitive or string literal.
    // also accepts hex (0xff), binary (0b101), char ('\n'), exp-form (1e9), and
    // the same set of visibility modifiers ConstValFix accepts.
    private val constEligibleVal = Regex(
        """^\s*(?:(?:private|internal|public)\s+)?val\s+\w+\s*(?::\s*[\w<>?]+)?\s*=\s*(?:""" +
            // string with no template
            """"[^"\\$]*"|""" +
            // numeric: hex / binary / decimal with optional exp / suffix
            """-?(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+\-]?\d+)?)[LlFfDdUu]*|""" +
            // bool
            """true|false|""" +
            // char (one regular char, or one escape)
            """'(?:\\[btnr'"\\]|[^'\\])'""" +
            """)\s*(?://.*)?$""",
        RegexOption.MULTILINE
    )

    fun scan(file: Path): StructuralMetrics {
        val text = file.readText()
        val loc = text.count { it == '\n' } + 1
        val constMatches = constVal.findAll(text).count()
        val plainMatches = plainVal.findAll(text).count()
        // count const-eligible vals only when they sit in a promotable scope
        // (top-level, `object` body, or `companion object` body). a `val x = 1`
        // in a method body or regular class body is not const-eligible no
        // matter what its RHS shape is, so counting it inflates the metric.
        // ConstValFix.computeScopeKind is the source of truth for what counts
        // as promotable.
        val lines = text.lines()
        val scope = ConstValFix.scopeKindForLines(lines)
        val constEligible = lines.withIndex()
            .filter { (idx, _) -> scope[idx] == ConstValFix.ScopeKind.PROMOTABLE }
            .count { (_, line) ->
                if (line.trimStart().startsWith("const ")) return@count false
                constEligibleVal.matchEntire(line) != null
            }
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
            lateinitVars = lateinitVar.findAll(text).count(),
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
