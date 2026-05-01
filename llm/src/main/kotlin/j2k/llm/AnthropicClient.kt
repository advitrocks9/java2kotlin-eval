package j2k.llm

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Tiny wrapper around the Anthropic Messages API. One endpoint, one method.
 * Hand-rolled JSON for the request body and a regex extractor for the
 * response text -- the dependency budget for this module is "java stdlib".
 *
 * The API key comes from `ANTHROPIC_API_KEY`. Fail fast with an actionable
 * message if it's missing -- a CI run that hits this code path is a bug
 * (CI scores committed fixtures, never calls the API).
 */
class AnthropicClient(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY")
        ?: error("ANTHROPIC_API_KEY not set. This module is local-only; CI evaluates committed fixtures."),
    private val model: String = "claude-sonnet-4-6",
    private val maxTokens: Int = 4096,
) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /**
     * One-shot completion. Returns the model's first text content block.
     * If the API errors, includes the response body in the thrown exception
     * so a local run shows the cause instead of just a 4xx code.
     */
    fun complete(systemPrompt: String, userPrompt: String): String {
        val body = buildBody(systemPrompt, userPrompt)
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("content-type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() / 100 != 2) {
            error("Anthropic API ${resp.statusCode()}: ${resp.body()}")
        }
        return extractText(resp.body())
    }

    private fun buildBody(systemPrompt: String, userPrompt: String): String = buildString {
        append('{')
        append("\"model\":\"").append(jsonStr(model)).append("\",")
        append("\"max_tokens\":").append(maxTokens).append(',')
        append("\"system\":\"").append(jsonStr(systemPrompt)).append("\",")
        append("\"messages\":[")
        append("{\"role\":\"user\",\"content\":\"").append(jsonStr(userPrompt)).append("\"}")
        append("]}")
    }

    /**
     * Pull the `text` field out of the first content block. The Messages
     * API response shape is:
     *   {"content":[{"type":"text","text":"..."}, ...], ...}
     * For our use case (one user message in, one text block out) this
     * regex is enough; if a future model returns multiple blocks we'd
     * need a real JSON parser. Worth flagging.
     */
    private fun extractText(body: String): String {
        val m = Regex("""\"text\"\s*:\s*\"((?:\\.|[^"\\])*)\"""").find(body)
            ?: error("no text field in Anthropic response: $body")
        return unescapeJsonStr(m.groupValues[1])
    }

    private fun jsonStr(s: String): String = buildString(s.length) {
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }

    private fun unescapeJsonStr(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'b' -> append('\b')
                    'f' -> append('')
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            append(hex.toInt(16).toChar())
                            i += 4
                        }
                    }
                    else -> { append('\\'); append(n) }
                }
                i += 2
            } else {
                append(c); i += 1
            }
        }
    }
}
