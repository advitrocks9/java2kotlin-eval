package j2keval

import java.nio.file.Files
import java.nio.file.Path

// joins two JSONL result files on `file` (relpath within each corpus) and
// emits a side-by-side report. concrete use here: i run the eval once on
// fixtures/edge-converted/ (--source=j2k) and once on
// fixtures/llm-claude-converted/ (--source=claude-sonnet-4-5); both are
// translations of the same edge-cases/ java. compare answers per-case
// which one compiled, who passed hypotheses, who didn't.
//
// only reads the fields the comparison needs (file, source, compile.ok,
// hypotheses[].passed) so adding new schema fields later doesn't break
// it. enough infra to plug a third source (gpt-5, gemini, whatever) in
// without rewriting the join.
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

    // returns each top-level object inside the named array as a raw string
    // fragment. quote-aware so brackets inside string literals don't count
    // toward depth -- without this a regex pattern stored as a string field
    // (e.g. `\\.use\\s*\\{`) trips the depth counter and the parser silently
    // clips the array.
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
