package j2keval

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Post-process J2K output: promote `val NAME = LITERAL` to `const val` when
 * the declaration is at top level OR inside a `companion object`, and the
 * RHS is a Kotlin compile-time constant: a primitive literal or a String
 * literal with no template expressions.
 *
 * Why this matters: Java `static final int X = 7` is a compile-time
 * constant -- callers can use it in annotations, switch labels, and
 * inlined contexts. J2K produces `val X = 7` inside a companion object,
 * which keeps the symbol visible from Java but loses the compile-time-
 * constant property in Kotlin. Adding `const` recovers that.
 *
 * Why I don't do this in PSI: an AST rewrite would be cleaner, but the
 * shape is regular enough for line-level matching, and skipping the PSI
 * setup keeps the eval module self-contained (no kotlin-compiler-embeddable
 * + Disposable wiring outside a test framework).
 *
 * Limits I accept:
 *   - we don't promote inside nested objects beyond one level
 *   - we don't recurse into arithmetic expressions; `val X = 1 + 2` stays
 *     plain `val` even though `const val X = 1 + 2` would compile.
 *     This is conservative -- we never produce a wrong `const`, only miss
 *     some opportunities. False negatives are reported as
 *     "constEligibleVal" by the metrics so a reader can see how many we
 *     left on the table.
 */
object ConstValFix {

    private val literalRhs = Regex(
        // val NAME[: TYPE] = LITERAL
        // LITERAL is one of: int, long, float, double, char, boolean, plain string
        """^(\s*)(val\s+\w+(?:\s*:\s*[\w<>?]+)?\s*=\s*(?:""\s*"[^"]*"\s*""|"[^"\\$]*"|-?\d[\d_]*[LlFfDd]?|true|false|'[^'\\]'|\d+\.\d+[fFdD]?))(\s*)$""",
        RegexOption.MULTILINE
    )

    /**
     * Returns the count of promotions applied and the rewritten text.
     * Promotion only happens when the line sits inside a top-level scope or
     * a companion object body -- we approximate that with a brace-depth
     * pass over the file.
     */
    fun rewrite(source: String): Pair<Int, String> {
        val lines = source.lines()
        val scope = computeScopeKind(lines)
        var promoted = 0
        val out = lines.mapIndexed { idx, line ->
            if (scope[idx] != ScopeKind.PROMOTABLE) return@mapIndexed line
            if (line.trimStart().startsWith("const ")) return@mapIndexed line
            val match = literalRhs.matchEntire(line) ?: return@mapIndexed line
            val indent = match.groupValues[1]
            val core = match.groupValues[2].replaceFirst("val ", "const val ")
            val tail = match.groupValues[3]
            promoted += 1
            "$indent$core$tail"
        }
        return promoted to out.joinToString("\n")
    }

    /**
     * For each line, decide whether a `val` declared at this position would
     * be a sensible promotion target. PROMOTABLE = top-level OR inside a
     * `companion object` body (one level deep, no nested classes between).
     */
    private fun computeScopeKind(lines: List<String>): List<ScopeKind> {
        val result = mutableListOf<ScopeKind>()
        // Stack of scope kinds: TOP -> CLASS or COMPANION on enter, pop on '}'
        val stack = ArrayDeque<ScopeKind>()
        stack.addLast(ScopeKind.PROMOTABLE)

        for (line in lines) {
            val trimmed = line.trim()
            // record the kind BEFORE updating the stack -- the val declared
            // on a line lives in the scope present at line start.
            result += stack.last()

            // `companion object` and top-level `object` are both PROMOTABLE
            // -- both compile `const val`. Regular `class` / `interface` /
            // `enum class` are not.
            val opensCompanion = Regex("""\bcompanion\s+object\b[^{]*\{""").containsMatchIn(line)
            val opensObject = !opensCompanion && Regex("""(^|\s)object\s+\w+[^{]*\{""").containsMatchIn(line)
            val opensClass = !opensCompanion && !opensObject && Regex("""\b(class|interface|enum\s+class)\b[^{]*\{""").containsMatchIn(line)
            val opensFun = Regex("""\bfun\b[^{]*\{""").containsMatchIn(line)
            val opensInit = Regex("""\binit\s*\{""").containsMatchIn(line)
            // count net brace delta on the line
            val opens = line.count { it == '{' }
            val closes = line.count { it == '}' }

            if (opensCompanion || opensObject) {
                stack.addLast(ScopeKind.PROMOTABLE)
            } else if (opensClass || opensFun || opensInit) {
                stack.addLast(ScopeKind.NOT_PROMOTABLE)
            } else if (opens > closes) {
                // anonymous block opener -- treat conservatively
                repeat(opens - closes) { stack.addLast(ScopeKind.NOT_PROMOTABLE) }
            }
            if (closes > opens) {
                repeat((closes - opens).coerceAtMost(stack.size - 1)) { stack.removeLast() }
            }
            if (trimmed.endsWith("{") && !opensCompanion && !opensClass && !opensFun && !opensInit) {
                // already handled above by opens delta; nothing more.
            }
        }
        return result
    }

    private enum class ScopeKind { PROMOTABLE, NOT_PROMOTABLE }
}

/** CLI: `j2keval-fix <kt-dir>` rewrites every .kt file in place and prints a summary. */
fun runConstValFix(ktDir: Path): Int {
    val files = collectKotlinFiles(ktDir)
    var totalPromoted = 0
    var changedFiles = 0
    for (f in files) {
        val src = f.readText()
        val (n, rewritten) = ConstValFix.rewrite(src)
        if (n > 0) {
            f.writeText(rewritten)
            totalPromoted += n
            changedFiles += 1
            println("[fix] $f promoted=$n")
        }
    }
    println("[fix] done: ${changedFiles} files changed, ${totalPromoted} val -> const val")
    return totalPromoted
}
