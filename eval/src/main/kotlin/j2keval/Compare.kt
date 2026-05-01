package j2keval

import java.nio.file.Files
import java.nio.file.Path

/**
 * Joins two JSONL result files and produces a side-by-side comparison
 * report. Pairs records on `file` (relative path within each corpus).
 *
 * Use case: I run the eval once over `fixtures/edge-converted/` (the
 * static J2K output, --source=j2k) and once over
 * `fixtures/llm-claude-converted/` (the LLM translation,
 * --source=claude-sonnet-4-6). Both corpora translate the same Java
 * inputs (the edge-cases/ folder, flattened). The compare subcommand
 * answers: per case, which one compiled, which one passed hypotheses,
 * how do their structural metrics line up.
 *
 * This is the bones of a multi-agent benchmarking dashboard. The full
 * version (model-vs-model leaderboard, per-category breakdowns, etc.)
 * lives outside this submission's scope, but the schema and join logic
 * are here for it.
 *
 * Parses JSONL with a hand-rolled minimal reader -- only reads the
 * fields the comparison cares about, ignores the rest. A future shape
 * change to the schema (adding fields) doesn't break this.
 */
object Compare {

    fun run(aPath: Path, bPath: Path, outPath: Path?) {
        val a = readSamples(aPath)
        val b = readSamples(bPath)
        val sourceA = a.firstOrNull()?.source ?: "?"
        val sourceB = b.firstOrNull()?.source ?: "?"
        val byFileA = a.associateBy { it.file }
        val byFileB = b.associateBy { it.file }
        val allFiles = (byFileA.keys + byFileB.keys).toSortedSet()

        val report = buildString {
            appendLine("# J2K compare report")
            appendLine()
            appendLine("- A: `$aPath` (${a.size} samples, source `$sourceA`)")
            appendLine("- B: `$bPath` (${b.size} samples, source `$sourceB`)")
            appendLine("- joined on `file` (relpath under each corpus)")
            appendLine()

            val bothCompile = allFiles.count { f ->
                (byFileA[f]?.compileOk == true) && (byFileB[f]?.compileOk == true)
            }
            val onlyA = allFiles.count { f ->
                (byFileA[f]?.compileOk == true) && (byFileB[f]?.compileOk != true)
            }
            val onlyB = allFiles.count { f ->
                (byFileA[f]?.compileOk != true) && (byFileB[f]?.compileOk == true)
            }
            val neither = allFiles.size - bothCompile - onlyA - onlyB
            appendLine("## Compile cross-tab")
            appendLine()
            appendLine("| outcome | count |")
            appendLine("|---------|-------|")
            appendLine("| both compile | $bothCompile |")
            appendLine("| only A (`$sourceA`) | $onlyA |")
            appendLine("| only B (`$sourceB`) | $onlyB |")
            appendLine("| neither | $neither |")
            appendLine()

            appendLine("## Per-file comparison")
            appendLine()
            appendLine("| file | A compile | A hyp | B compile | B hyp |")
            appendLine("|------|-----------|-------|-----------|-------|")
            for (f in allFiles) {
                val ra = byFileA[f]
                val rb = byFileB[f]
                appendLine(
                    "| `$f` | ${render(ra?.compileOk)} | ${ra?.hypothesisSummary() ?: "n/a"} " +
                        "| ${render(rb?.compileOk)} | ${rb?.hypothesisSummary() ?: "n/a"} |"
                )
            }
            appendLine()
        }
        if (outPath != null) {
            outPath.toFile().parentFile?.mkdirs()
            outPath.toFile().writeText(report)
            println("[compare] wrote $outPath")
        } else {
            println(report)
        }
    }

    private fun render(b: Boolean?): String = when (b) {
        null -> "missing"
        true -> "yes"
        false -> "**no**"
    }

    private data class CompareSample(
        val file: String,
        val source: String,
        val compileOk: Boolean,
        val hypothesisPassed: Int,
        val hypothesisTotal: Int,
    ) {
        fun hypothesisSummary(): String =
            if (hypothesisTotal == 0) "-" else "$hypothesisPassed/$hypothesisTotal"
    }

    private fun readSamples(path: Path): List<CompareSample> {
        require(Files.exists(path)) { "JSONL not found: $path" }
        return Files.readAllLines(path)
            .filter { it.isNotBlank() }
            .map { line ->
                val file = pickStr(line, "file") ?: error("missing file field in $line")
                val source = pickStr(line, "source") ?: "?"
                val compileOk = pickBoolInside(line, "compile", "ok") ?: false
                val hyps = pickArray(line, "hypotheses")
                val total = hyps.size
                val passed = hyps.count { fragment ->
                    Regex("""\"passed\"\s*:\s*true""").containsMatchIn(fragment)
                }
                CompareSample(file, source, compileOk, passed, total)
            }
    }

    // String value of a top-level scalar key. Doesn't handle nested
    // strings -- good enough for our pickStr use cases.
    private fun pickStr(line: String, key: String): String? {
        val m = Regex("""\"$key\"\s*:\s*\"((?:\\.|[^\\"])*)\"""").find(line) ?: return null
        return m.groupValues[1]
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun pickBoolInside(line: String, outerKey: String, innerKey: String): Boolean? {
        val outer = Regex("""\"$outerKey\"\s*:\s*(\{[^{}]*\})""").find(line) ?: return null
        val m = Regex("""\"$innerKey\"\s*:\s*(true|false)""").find(outer.groupValues[1]) ?: return null
        return m.groupValues[1] == "true"
    }

    /**
     * Returns each top-level object inside the named array as a raw string
     * fragment. Quote-aware: brackets that appear inside a JSON string
     * literal don't count toward depth tracking. Without this guard a
     * regex pattern stored in a string field (e.g. `\\.use\\s*\\{`) would
     * unbalance the depth counter.
     */
    private fun pickArray(line: String, key: String): List<String> {
        val start = line.indexOf("\"$key\"").takeIf { it >= 0 } ?: return emptyList()
        val open = line.indexOf('[', start).takeIf { it >= 0 } ?: return emptyList()
        var depthA = 1; var i = open + 1
        val items = mutableListOf<String>()
        var objStart = -1; var objDepth = 0
        var inStr = false
        while (i < line.length && depthA > 0) {
            val c = line[i]
            if (inStr) {
                if (c == '\\' && i + 1 < line.length) {
                    i += 2; continue
                }
                if (c == '"') inStr = false
                i += 1; continue
            }
            when (c) {
                '"' -> inStr = true
                '[' -> depthA += 1
                ']' -> depthA -= 1
                '{' -> {
                    if (objDepth == 0) objStart = i
                    objDepth += 1
                }
                '}' -> {
                    objDepth -= 1
                    if (objDepth == 0 && objStart >= 0) {
                        items += line.substring(objStart, i + 1)
                        objStart = -1
                    }
                }
            }
            i += 1
        }
        return items
    }
}
