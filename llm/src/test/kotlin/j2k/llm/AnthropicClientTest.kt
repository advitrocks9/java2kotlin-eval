package j2k.llm

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// tests for the response-parsing seam without hitting the network.
// extractText is private and only reachable via complete() over a real
// HTTP call, so we reflect in. fake api key just to dodge the no-key
// error in the constructor.
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
        // hypothetical future field with "text" outside content[]. only
        // content[].text should contribute.
        val body = """{"content":[{"type":"text","text":"real"}],"meta":{"text":"should not appear"}}"""
        assertEquals("real", call(body))
    }

    @Test
    fun `tool_use blocks with nested text in input are NOT treated as text`() {
        // tool_use blocks have shape {"type":"tool_use","input":{"text":"..."}}
        // -- the "text" key inside `input` is a tool argument, not output text.
        // a naive regex over the whole content[] string would pull it in.
        val body = """{"content":[
            {"type":"text","text":"hello"},
            {"type":"tool_use","id":"x","name":"foo","input":{"text":"DO_NOT_LEAK"}},
            {"type":"text","text":" world"}
        ]}"""
        assertEquals("hello world", call(body), "tool_use input.text should never leak into the assistant response")
    }

    @Test
    fun `non-text block types between text blocks are skipped, surrounding text concatenates`() {
        val body = """{"content":[
            {"type":"text","text":"a"},
            {"type":"image","source":{"type":"base64","data":"ZGF0YQ=="}},
            {"type":"text","text":"b"}
        ]}"""
        assertEquals("ab", call(body))
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
