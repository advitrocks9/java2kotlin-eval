package j2keval

import java.nio.file.Path
import java.time.Instant

object Report {
    fun render(
        ktDir: Path,
        compile: List<CompileResult>,
        structural: List<StructuralMetrics>,
        hypotheses: Map<String, HypothesisCheck>,
    ): String = buildString {
        val total = compile.size
        val passed = compile.count { it.ok }
        val passRate = if (total == 0) 0.0 else passed.toDouble() / total
        appendLine("# J2K eval report")
        appendLine()
        appendLine("- corpus: `$ktDir`")
        appendLine("- generated: ${Instant.now()}")
        appendLine("- files: $total")
        appendLine("- kotlinc pass rate: ${"%.1f".format(passRate * 100)}% ($passed/$total)")
        appendLine()

        appendLine("## Compile summary")
        appendLine()
        appendLine("| file | ok | errs | ms |")
        appendLine("|------|----|------|----|")
        for (r in compile.sortedBy { it.file.toString() }) {
            val mark = if (r.ok) "yes" else "**no**"
            val errs = r.errors.size
            appendLine("| `${ktDir.relativize(r.file)}` | $mark | $errs | ${r.durationMs} |")
        }
        appendLine()

        // Aggregate compile-error buckets
        val buckets = mutableMapOf<String, Int>()
        for (r in compile) {
            for (e in r.errors) {
                val key = bucketize(e)
                buckets[key] = (buckets[key] ?: 0) + 1
            }
        }
        if (buckets.isNotEmpty()) {
            appendLine("### Compile-error buckets")
            appendLine()
            appendLine("| bucket | count |")
            appendLine("|--------|-------|")
            buckets.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                appendLine("| $k | $v |")
            }
            appendLine()
        }

        appendLine("## Structural metrics (aggregate)")
        appendLine()
        val totals = structural.fold(IntArray(11)) { acc, m ->
            acc[0] += m.locKotlin
            acc[1] += m.notNullAsserts
            acc[2] += m.anonymousObjects
            acc[3] += m.funInterface
            acc[4] += m.constVal
            acc[5] += m.plainVal
            acc[6] += m.constEligibleVal
            acc[7] += m.javaThrowsAnnotations
            acc[8] += m.innerClass
            acc[9] += m.varargParams
            acc[10] += m.useBlocks
            acc
        }
        appendLine("| metric | total |")
        appendLine("|--------|-------|")
        appendLine("| LOC (Kotlin) | ${totals[0]} |")
        appendLine("| `!!` not-null asserts | ${totals[1]} |")
        appendLine("| `object :` literal anon classes | ${totals[2]} |")
        appendLine("| `fun interface` declarations | ${totals[3]} |")
        appendLine("| `const val` declarations | ${totals[4]} |")
        appendLine("| `val` declarations (non-const) | ${totals[5]} |")
        appendLine("| `val` with literal RHS that COULD be `const val` | ${totals[6]} |")
        appendLine("| `@Throws(...)` annotations | ${totals[7]} |")
        appendLine("| `inner class` declarations | ${totals[8]} |")
        appendLine("| `vararg` params | ${totals[9]} |")
        appendLine("| `.use {}` resource blocks | ${totals[10]} |")
        appendLine()

        if (hypotheses.isNotEmpty()) {
            appendLine("## Hypothesis checks")
            appendLine()
            appendLine("Each row tests a single claim about how J2K should handle a Java idiom.")
            appendLine()
            appendLine("| file | tag | passed | expectation | sample |")
            appendLine("|------|-----|--------|-------------|--------|")
            for ((file, h) in hypotheses) {
                val mark = if (h.passed) "yes" else "**no**"
                val sample = (h.actualSnippet ?: "").replace("|", "\\|").replace("\n", " ").take(80)
                appendLine("| `$file` | ${h.tag} | $mark | ${h.expectation} | `$sample` |")
            }
            appendLine()
        }
    }

    /**
     * Map a kotlinc error line to a coarse-grained bucket. The phrasing has
     * shifted across compiler versions; we match on the part most likely to
     * stay stable (the diagnostic code or its core verb).
     */
    private fun bucketize(line: String): String = when {
        "unresolved reference" in line -> "unresolved reference"
        "type mismatch" in line -> "type mismatch"
        "expecting" in line -> "syntax: expecting ..."
        "platform declaration clash" in line -> "platform declaration clash"
        "modifier" in line && "not applicable" in line -> "modifier not applicable"
        "abstract" in line -> "abstract / abstract member"
        "null" in line -> "nullability"
        "vararg" in line -> "vararg"
        else -> "other"
    }
}
