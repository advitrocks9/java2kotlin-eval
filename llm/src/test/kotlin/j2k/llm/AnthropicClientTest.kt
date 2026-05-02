package j2k.llm

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests the response-parsing seam without touching the network. The
 * extractText path is reached via the public complete() only when wired
 * to a real HTTP call, so we test the parser by reflecting in via a
 * helper. AnthropicClient is constructed with a fake key so the no-key
 * error doesn't fire.
 */
class AnthropicClientTest {

    private val client = AnthropicClient(apiKey = "fake")
    private val extract = AnthropicClient::class.java
        .getDeclaredMethod("extractText", String::class.java)
        .apply { isAccessible = true }

    private fun call(body: String): String = try {
        extract.invoke(client, body) as String
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    @Test
    fun `single text block returns its contents`() {
        val body = """{"content":[{"type":"text","text":"package x\n\nclass A"}],"role":"assistant"}"""
        assertEquals("package x\n\nclass A", call(body))
    }

    @Test
    fun `multiple text blocks are concatenated in order`() {
        val body = """{"content":[{"type":"text","text":"part one"},{"type":"text","text":" part two"}],"role":"assistant"}"""
        assertEquals("part one part two", call(body))
    }

    @Test
    fun `unrelated text fields outside content are ignored`() {
        // Hypothetical future field with "text" outside content[]. Only
        // content[].text should contribute.
        val body = """{"content":[{"type":"text","text":"real"}],"meta":{"text":"should not appear"}}"""
        assertEquals("real", call(body))
    }

    @Test
    fun `escape sequences in text are unescaped`() {
        val body = """{"content":[{"type":"text","text":"line\nnext\ttab\"quote"}]}"""
        assertEquals("line\nnext\ttab\"quote", call(body))
    }

    @Test
    fun `missing content array fails fast with the body in the error`() {
        val body = """{"role":"assistant","stop_reason":"end_turn"}"""
        val ex = assertFailsWith<IllegalStateException> { call(body) }
        assert("content" in ex.message.orEmpty()) { "expected content-mentioning error, got ${ex.message}" }
    }
}
