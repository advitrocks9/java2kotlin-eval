package j2k.llm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Walks a Java input directory, calls Claude on each .java file, writes the
 * Kotlin output to a mirrored relative path under outputDir. Skips files
 * that already exist in outputDir (idempotent re-run after a partial
 * failure -- API calls aren't free and a flaky retry shouldn't redo every
 * successful translation).
 *
 * The prompt is in `Prompt.kt`. Keep it tight; the model gets the whole
 * .java contents in the user message and is told to output Kotlin only.
 *
 * What this isn't: a benchmarking harness. There's no temperature sweep,
 * no multi-shot retry, no scoring loop. Translation only -- the eval
 * module scores the captured outputs after the fact.
 */
class Translator(
    /**
     * (system, user) -> assistant text. Function-injected so tests can stub
     * without standing up an Anthropic client. Production wiring passes
     * `AnthropicClient::complete`.
     */
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

    /**
     * Recover Kotlin source from a model response that may have:
     *   - markdown fences (```kotlin ... ```)
     *   - a prose preamble before the fence ("Here is the translation:")
     *   - both
     *   - neither (clean Kotlin, the prompt's stated contract)
     *
     * Strategy: if a fence exists, return its contents. Otherwise scan for
     * the first line that starts with a Kotlin top-level token (`package`,
     * `import`, `class`, `object`, `interface`, `fun`, `enum`, an
     * annotation, or a single-line comment) and drop everything before it.
     * If nothing matches, return the trimmed input as-is and let kotlinc
     * complain -- better to fail compile loudly than silently emit prose.
     */
    private fun stripWrappers(s: String): String {
        // 1. fence path: take what's inside the first ```...``` block.
        val fenceStart = Regex("```(?:kotlin)?\\s*\n", RegexOption.IGNORE_CASE).find(s)
        if (fenceStart != null) {
            val afterOpen = s.substring(fenceStart.range.last + 1)
            val close = afterOpen.indexOf("```")
            if (close >= 0) {
                return afterOpen.substring(0, close).trimEnd() + "\n"
            }
        }
        // 2. prose-preamble path: skip lines until one looks like Kotlin code.
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
