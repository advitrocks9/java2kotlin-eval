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
        // [visibility ]val NAME[: TYPE] = LITERAL [// trailing-comment]
        // visibility = optional private | internal | public
        // LITERAL: string with no template, hex/bin/decimal numeric (with
        // optional exp + L/F/D suffix), bool, char literal (incl escapes).
        """^(\s*)((?:(?:private|internal|public)\s+)?val\s+\w+(?:\s*:\s*[\w<>?]+)?\s*=\s*(?:""" +
            """"[^"\\$]*"|""" +
            """-?(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+\-]?\d+)?)[LlFfDdUu]*|""" +
            """true|false|""" +
            """'(?:\\[btnr'"\\]|[^'\\])'""" +
            """))(\s*(?://.*)?)$""",
        RegexOption.MULTILINE
    )

    /**
     * Returns the count of promotions applied and the rewritten text.
     * Promotion only happens when the line sits inside a top-level scope,
     * an `object` body, or a `companion object` body. We approximate scope
     * with a brace-depth pass over the file (after string/comment scrubbing
     * so `val msg = "}{"` doesn't lie about depth).
     */
    fun rewrite(source: String): Pair<Int, String> {
        val lines = source.lines()
        val scope = scopeKindForLines(lines)
        var promoted = 0
        val out = lines.mapIndexed { idx, line ->
            if (scope[idx] != ScopeKind.PROMOTABLE) return@mapIndexed line
            if (line.trimStart().startsWith("const ")) return@mapIndexed line
            // Skip vals that already carry the const modifier even after a
            // visibility prefix: `private const val ...`.
            val withoutVis = line.trimStart()
                .removePrefix("private ").removePrefix("internal ").removePrefix("public ")
                .trimStart()
            if (withoutVis.startsWith("const ")) return@mapIndexed line
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
     * `companion object` / `object` body (no enclosing fun/class between).
     *
     * Public so Metrics.scan can re-use the same scope walk for
     * constEligibleVal counting -- the metric and the fix should agree on
     * what counts.
     */
    fun scopeKindForLines(lines: List<String>): List<ScopeKind> {
        val result = mutableListOf<ScopeKind>()
        // Stack of scope kinds: TOP -> CLASS or COMPANION on enter, pop on '}'
        val stack = ArrayDeque<ScopeKind>()
        stack.addLast(ScopeKind.PROMOTABLE)

        for (line in lines) {
            // record the kind BEFORE updating the stack -- the val declared
            // on a line lives in the scope present at line start.
            result += stack.last()

            // strip line comments and string literals before any brace
            // counting so `val msg = "}{"` and `// foo {` don't move the
            // depth. block comments are rare in J2K output and would need a
            // second pass; the cases I've seen don't carry them on
            // declaration lines.
            val scrubbed = stripStringsAndLineComments(line)

            // `companion object` and top-level `object` are both PROMOTABLE
            // -- both compile `const val`. Regular `class` / `interface` /
            // `enum class` are not.
            val opensCompanion = Regex("""\bcompanion\s+object\b[^{]*\{""").containsMatchIn(scrubbed)
            val opensObject = !opensCompanion && Regex("""(^|\s)object\s+\w+[^{]*\{""").containsMatchIn(scrubbed)
            val opensClass = !opensCompanion && !opensObject && Regex("""\b(class|interface|enum\s+class)\b[^{]*\{""").containsMatchIn(scrubbed)
            val opensFun = Regex("""\bfun\b[^{]*\{""").containsMatchIn(scrubbed)
            val opensInit = Regex("""\binit\s*\{""").containsMatchIn(scrubbed)
            val opens = scrubbed.count { it == '{' }
            val closes = scrubbed.count { it == '}' }

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
        }
        return result
    }

    /**
     * Replace every char inside a "..." string literal with a space, then
     * truncate at the first `//`. Returns a string of the same length up to
     * the truncation point so brace counts and column-based regex still line
     * up. Doesn't try to handle multi-line raw strings ("""...""") -- those
     * span lines, and J2K doesn't emit them on declaration lines I've seen.
     */
    private fun stripStringsAndLineComments(line: String): String {
        val sb = StringBuilder(line.length)
        var i = 0
        var inStr = false
        while (i < line.length) {
            val c = line[i]
            if (inStr) {
                if (c == '\\' && i + 1 < line.length) {
                    sb.append(' '); sb.append(' '); i += 2; continue
                }
                if (c == '"') { sb.append(c); inStr = false; i += 1; continue }
                sb.append(' '); i += 1; continue
            }
            if (c == '"') { sb.append(c); inStr = true; i += 1; continue }
            if (c == '/' && i + 1 < line.length && line[i + 1] == '/') break
            sb.append(c); i += 1
        }
        return sb.toString()
    }

    enum class ScopeKind { PROMOTABLE, NOT_PROMOTABLE }
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
