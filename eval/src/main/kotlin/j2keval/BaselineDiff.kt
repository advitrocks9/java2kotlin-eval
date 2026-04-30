package j2keval

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.AbstractDelta
import java.nio.file.Files
import java.nio.file.Path

// diff a candidate .kt corpus against a reference one, paired by relpath.
// the point: counting !! and const val occurrences only tells you so much.
// if a second source of kotlin (LLM, future converter, runner output minus
// the IDE post-processing) translates the same java the JetBrains testData
// covers, a line-level diff against that baseline is a much louder signal.
//
// normalization is light -- trim trailing whitespace, collapse blank-line
// runs, drop trailing newlines. NOT a semantic diff. import order,
// identifier renames, formatting differences will all still show up. fine
// by me, that's what i'd want to see in a review.
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
