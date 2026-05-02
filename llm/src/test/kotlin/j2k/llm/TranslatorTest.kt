package j2k.llm

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// no live api in tests. stub the complete fn directly via the function-
// injected ctor and verify file walking, output mirroring, skip-existing.
class TranslatorTest {

    @Test
    fun `mirrors directory structure into output, skips already-translated files`() {
        val tmp = Files.createTempDirectory("llm-")
        val input = tmp.resolve("in").also { it.toFile().mkdirs() }
        val output = tmp.resolve("out").also { it.toFile().mkdirs() }
        input.resolve("a.java").writeText("class A {}\n")
        input.resolve("nested").toFile().mkdirs()
        input.resolve("nested/b.java").writeText("class B {}\n")

        val replies = mutableListOf("class A\n", "class B\n")
        var calls = 0
        val tx = Translator(complete = { _, _ ->
            calls += 1
            replies.removeAt(0)
        })
        tx.translateCorpus(input, output)

        assertEquals(2, calls)
        assertTrue(output.resolve("a.kt").toFile().exists())
        assertTrue(output.resolve("nested/b.kt").toFile().exists())
        assertEquals("class A\n", output.resolve("a.kt").readText())
        assertEquals("class B\n", output.resolve("nested/b.kt").readText())

        // Re-run without overwrite -> zero new API calls; outputs unchanged.
        var calls2 = 0
        val tx2 = Translator(complete = { _, _ -> calls2 += 1; "should not be called" })
        tx2.translateCorpus(input, output)
        assertEquals(0, calls2, "skip-existing should mean zero API calls on re-run")
    }

    @Test
    fun `strips markdown fences if the model adds them`() {
        val tmp = Files.createTempDirectory("llm-fences-")
        val input = tmp.resolve("in").also { it.toFile().mkdirs() }
        val output = tmp.resolve("out").also { it.toFile().mkdirs() }
        input.resolve("a.java").writeText("class A {}\n")

        val tx = Translator(complete = { _, _ -> "```kotlin\nclass A\n```\n" })
        tx.translateCorpus(input, output)

        val written = output.resolve("a.kt").readText()
        assertEquals("class A\n", written, "fences should be stripped, trailing newline preserved")
    }

    @Test
    fun `prose preamble before kotlin code is stripped`() {
        val tmp = Files.createTempDirectory("llm-prose-")
        val input = tmp.resolve("in").also { it.toFile().mkdirs() }
        val output = tmp.resolve("out").also { it.toFile().mkdirs() }
        input.resolve("a.java").writeText("class A {}\n")

        // Model occasionally adds a prose preamble despite the system prompt.
        // Without the guard, the prose lands in the .kt and kotlinc dies.
        val tx = Translator(complete = { _, _ ->
            "Here's the Kotlin translation:\n\nThe class is straightforward.\n\npackage com.example\n\nclass A\n"
        })
        tx.translateCorpus(input, output)

        val written = output.resolve("a.kt").readText()
        assertEquals("package com.example\n\nclass A\n", written)
    }

    @Test
    fun `prose preamble plus fences both stripped`() {
        val tmp = Files.createTempDirectory("llm-prose-fence-")
        val input = tmp.resolve("in").also { it.toFile().mkdirs() }
        val output = tmp.resolve("out").also { it.toFile().mkdirs() }
        input.resolve("a.java").writeText("class A {}\n")

        val tx = Translator(complete = { _, _ ->
            "Here you go:\n\n```kotlin\npackage com.example\n\nclass A\n```\n"
        })
        tx.translateCorpus(input, output)

        val written = output.resolve("a.kt").readText()
        assertEquals("package com.example\n\nclass A\n", written)
    }
}
