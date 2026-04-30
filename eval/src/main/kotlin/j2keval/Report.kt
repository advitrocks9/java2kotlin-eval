package j2keval

import java.nio.file.Path
import java.time.Instant

object Report {
    fun render(
        ktDir: Path,
        compile: List<CompileResult>,
        structural: List<StructuralMetrics>,
        psi: List<PsiMetrics> = emptyList(),
        hypotheses: List<Pair<String, HypothesisCheck>>,
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

        if (psi.isNotEmpty()) {
            // PSI numbers should match the regex numbers in most cases, but
            // diverge where regex was wrong: object-literal expressions
            // nested in larger structures, !! inside string templates, vals
            // inside unusual scopes.
            val psiTotals = IntArray(8)
            for (m in psi) {
                psiTotals[0] += m.notNullAsserts
                psiTotals[1] += m.objectLiteralExprs
                psiTotals[2] += m.funInterfaces
                psiTotals[3] += m.constVals
                psiTotals[4] += m.plainVals
                psiTotals[5] += m.constEligibleVals
                psiTotals[6] += m.innerClasses
                psiTotals[7] += m.varargParams
            }
            appendLine("## Structural metrics (PSI -- KotlinCoreEnvironment)")
            appendLine()
            appendLine("| metric | regex | psi | delta |")
            appendLine("|--------|-------|-----|-------|")
            val regexAgg = structural.fold(IntArray(8)) { acc, m ->
                acc[0] += m.notNullAsserts; acc[1] += m.anonymousObjects; acc[2] += m.funInterface
                acc[3] += m.constVal; acc[4] += m.plainVal; acc[5] += m.constEligibleVal
                acc[6] += m.innerClass; acc[7] += m.varargParams
                acc
            }
            val labels = listOf(
                "!! not-null asserts",
                "object expression (anon class)",
                "fun interface",
                "const val",
                "val (non-const)",
                "const-eligible val",
                "inner class",
                "vararg",
            )
            for (i in labels.indices) {
                val delta = psiTotals[i] - regexAgg[i]
                val mark = if (delta == 0) "0" else if (delta > 0) "+$delta" else "$delta"
                appendLine("| ${labels[i]} | ${regexAgg[i]} | ${psiTotals[i]} | $mark |")
            }
            appendLine()
        }

        if (hypotheses.isNotEmpty()) {
            val passed = hypotheses.count { it.second.passed }
            val total = hypotheses.size
            appendLine("## Hypothesis checks")
            appendLine()
            appendLine("Each row tests a single claim about how J2K should handle a Java idiom. Pass rate: $passed/$total.")
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
