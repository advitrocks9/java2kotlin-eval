package j2keval

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.AbstractDelta
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compare a candidate .kt corpus against a reference baseline corpus. Pairs
 * by relative path. The motivation: the JetBrains testData under
 * `fixtures/newj2k/` is regression-locked output -- exactly what the
 * intellij-community J2K converter is verified to produce today. If a
 * separately-produced .kt corpus (LLM, future converter, my runner with the
 * IDE post-processing bypassed) lines up against the same Java input, a
 * line-level diff against the baseline is a much louder correctness signal
 * than counting !! occurrences.
 *
 * Normalization is intentional and modest: trim trailing whitespace, collapse
 * blank-line runs to one, drop a trailing newline. This is *not* a semantic
 * diff -- import order, identifier renames, formatting differences will
 * still surface as hunks. That's fine: a reviewer wants to see those.
 */
data class BaselineComparison(
    val file: String,             // relpath under candidate corpus
    val identical: Boolean,
    val deltaCount: Int,
    val unifiedDiff: String?,     // null when identical or baseline missing
    val baselineMissing: Boolean,
)

object BaselineDiff {

    fun compareCorpus(candidateRoot: Path, baselineRoot: Path): List<BaselineComparison> {
        val candidates = collectKotlinFiles(candidateRoot)
        return candidates.map { candidate ->
            val rel = candidateRoot.relativize(candidate).toString()
            val baseline = baselineRoot.resolve(rel)
            if (!Files.exists(baseline)) {
                BaselineComparison(
                    file = rel,
                    identical = false,
                    deltaCount = 0,
                    unifiedDiff = null,
                    baselineMissing = true,
                )
            } else {
                compareOne(rel, candidate, baseline)
            }
        }
    }

    fun compareOne(rel: String, candidate: Path, baseline: Path): BaselineComparison {
        val a = normalize(Files.readAllLines(baseline))
        val b = normalize(Files.readAllLines(candidate))
        if (a == b) {
            return BaselineComparison(
                file = rel,
                identical = true,
                deltaCount = 0,
                unifiedDiff = null,
                baselineMissing = false,
            )
        }
        val patch = DiffUtils.diff(a, b)
        val deltas: List<AbstractDelta<String>> = patch.deltas
        val unified = UnifiedDiffUtils.generateUnifiedDiff(
            "baseline/$rel", "candidate/$rel", a, patch, 3,
        ).joinToString("\n")
        return BaselineComparison(
            file = rel,
            identical = false,
            deltaCount = deltas.size,
            unifiedDiff = unified,
            baselineMissing = false,
        )
    }

    private fun normalize(lines: List<String>): List<String> {
        val trimmed = lines.map { it.trimEnd() }
        // collapse runs of blank lines to a single blank line; drop trailing
        // blank lines.
        val collapsed = mutableListOf<String>()
        var prevBlank = false
        for (l in trimmed) {
            val blank = l.isEmpty()
            if (blank && prevBlank) continue
            collapsed += l
            prevBlank = blank
        }
        while (collapsed.isNotEmpty() && collapsed.last().isEmpty()) collapsed.removeAt(collapsed.size - 1)
        return collapsed
    }
}
