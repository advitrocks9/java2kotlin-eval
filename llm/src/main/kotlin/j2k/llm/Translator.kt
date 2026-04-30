package j2k.llm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

// walks inputDir, sends each .java to complete(), drops the kotlin response
// at the matching relpath under outputDir. skips files already there so a
// re-run after one bad output doesn't re-pay the api call for the rest.
// translation only -- no temperature sweep, no retry loop. the eval module
// scores captured outputs after the fact.
class Translator(
    // (system, user) -> assistant text. injected so tests don't need a live
    // client. main wires AnthropicClient::complete.
    private val complete: (String, String) -> String,
    private val onFile: (String) -> Unit = { },
) {

    fun translateCorpus(inputDir: Path, outputDir: Path, overwrite: Boolean = false) {
        val javaFiles = Files.walk(inputDir).use { stream ->
            stream.filter { it.toString().endsWith(".java") }.sorted().toList()
        }
        require(javaFiles.isNotEmpty()) { "no .java files under $inputDir" }

        for (java in javaFiles) {
            val rel = inputDir.relativize(java).toString().removeSuffix(".java") + ".kt"
            val out = outputDir.resolve(rel)
            if (!overwrite && Files.exists(out)) {
                onFile("$rel (skipped, already exists)")
                continue
            }
            val javaText = java.readText()
            val kotlinText = complete(systemPrompt(), userPrompt(rel, javaText))
            val cleaned = stripWrappers(kotlinText)
            out.parent?.toFile()?.mkdirs()
            out.writeText(cleaned)
            onFile(rel)
        }
    }

    // models sometimes wrap output in ```kotlin fences or stick a "here's the
    // translation:" preamble before the code despite the prompt saying not to.
    // if there's a fence, take what's inside it. otherwise drop lines until
    // one looks like kotlin (package / import / @ / class / fun / etc). if
    // neither path triggers, return the input untouched and let kotlinc fail
    // loudly -- better than silently writing prose to a .kt.
    private fun stripWrappers(s: String): String {
        val fenceStart = Regex("```(?:kotlin)?\\s*\n", RegexOption.IGNORE_CASE).find(s)
        if (fenceStart != null) {
            val afterOpen = s.substring(fenceStart.range.last + 1)
            val close = afterOpen.indexOf("```")
            if (close >= 0) {
                return afterOpen.substring(0, close).trimEnd() + "\n"
            }
        }
        val kotlinStart = Regex(
            "^(?:package |import |@|class |object |interface |fun |enum class |sealed |data class |abstract |open |internal |private |public |//|/\\*)"
        )
        val lines = s.lines()
        val firstKotlinIdx = lines.indexOfFirst { kotlinStart.containsMatchIn(it.trimStart()) }
        return if (firstKotlinIdx > 0) {
            lines.drop(firstKotlinIdx).joinToString("\n").trimEnd() + "\n"
        } else {
            s.trim() + "\n"
        }
    }
}
