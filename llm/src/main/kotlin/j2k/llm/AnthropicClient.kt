package j2k.llm

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// minimal client for the messages api. one endpoint, one method. no SDK
// because pulling in a multi-MB lib for a single POST is silly.
//
// ANTHROPIC_API_KEY required. CI never hits this path -- if a CI run lands
// here something is wrong with the workflow.
class AnthropicClient(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY")
        ?: error("ANTHROPIC_API_KEY not set. This module is local-only; CI evaluates committed fixtures."),
    private val model: String = "claude-sonnet-4-5",
    private val maxTokens: Int = 4096,
) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // one-shot completion. on a 4xx/5xx, the response body lands in the
    // exception text so a local run sees the actual error from anthropic
    // instead of just the status code.
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

    // response shape: {"content":[{"type":"text","text":"..."}, ...], ...}
    // long responses can split across multiple text blocks so concatenate
    // them all. walks the top-level objects of content[] one by one and
    // only picks up "text" from blocks whose "type" is literally "text" --
    // a future tool_use block (which has its own nested "text" field
    // inside "input") shouldn't leak in.
    // no JSON dep, the shape is stable enough that the regex walk holds up.
    // surrogate-pair \uHHHH not combined into a single code point; non-BMP
    // in kotlin source is rare and i'll fix it if it ever bites.
    private fun extractText(body: String): String {
        val contentArray = extractContentArray(body)
            ?: error("no content array in Anthropic response: $body")
        val pieces = mutableListOf<String>()
        for (block in topLevelObjects(contentArray)) {
            // only look at the IMMEDIATE keys of this block, not nested ones.
            // type and text are top-level scalars in the messages api shape;
            // a regex against the full block fragment would also match nested
            // {"input":{"text":"..."}} in tool_use blocks.
            val type = topLevelStringField(block, "type") ?: continue
            if (type != "text") continue
            val text = topLevelStringField(block, "text") ?: continue
            pieces += text
        }
        if (pieces.isEmpty()) error("no text blocks in content[]: $contentArray")
        return pieces.joinToString(separator = "")
    }

    // split the inside of a JSON array into raw object fragments. quote- and
    // depth-aware so nested objects/arrays don't get mistaken for top-level
    // siblings.
    private fun topLevelObjects(arrayBody: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0; var start = -1; var inStr = false; var i = 0
        while (i < arrayBody.length) {
            val c = arrayBody[i]
            if (inStr) {
                if (c == '\\' && i + 1 < arrayBody.length) { i += 2; continue }
                if (c == '"') inStr = false
                i += 1; continue
            }
            when (c) {
                '"' -> inStr = true
                '{', '[' -> { if (depth == 0 && c == '{') start = i; depth += 1 }
                '}', ']' -> {
                    depth -= 1
                    if (depth == 0 && c == '}' && start >= 0) {
                        out += arrayBody.substring(start, i + 1)
                        start = -1
                    }
                }
            }
            i += 1
        }
        return out
    }

    // pull the value of a string field at the IMMEDIATE top level of an
    // object body (walks the body, skips into nested objects/arrays without
    // descending). when at depth 1 and we hit a `"`, parse a string -- if
    // it's followed by `:` it's a key (compare to the target and either
    // extract the value or skip), otherwise it's a value scalar so we just
    // advance past it.
    private fun topLevelStringField(objectBody: String, key: String): String? {
        var depth = 0; var i = 0
        while (i < objectBody.length) {
            val c = objectBody[i]
            when {
                c == '"' -> {
                    val end = scanStringEnd(objectBody, i + 1)
                    if (end < 0) return null
                    if (depth == 1) {
                        val literal = objectBody.substring(i + 1, end)
                        var j = end + 1
                        while (j < objectBody.length && objectBody[j].isWhitespace()) j += 1
                        val isKey = j < objectBody.length && objectBody[j] == ':'
                        if (isKey && literal == key) {
                            // found the key. step past `:` and grab the next
                            // string value.
                            var k = j + 1
                            while (k < objectBody.length && objectBody[k].isWhitespace()) k += 1
                            if (k >= objectBody.length || objectBody[k] != '"') return null
                            val valueEnd = scanStringEnd(objectBody, k + 1)
                            if (valueEnd < 0) return null
                            return unescapeJsonStr(objectBody.substring(k + 1, valueEnd))
                        }
                    }
                    i = end + 1; continue
                }
                c == '{' || c == '[' -> { depth += 1; i += 1 }
                c == '}' || c == ']' -> { depth -= 1; i += 1 }
                else -> i += 1
            }
        }
        return null
    }

    // returns the index of the closing `"` of a JSON string starting at `from`
    // (which is the first char AFTER the opening quote). -1 if unterminated.
    private fun scanStringEnd(body: String, from: Int): Int {
        var i = from
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) { i += 2; continue }
            if (c == '"') return i
            i += 1
        }
        return -1
    }

    // pull out the substring between `"content":[` and its matching `]`.
    // quote-aware so a `]` inside a string literal doesn't unbalance things.
    // same idea as Compare.pickArray; both could share a helper but the
    // duplication is small.
    private fun extractContentArray(body: String): String? {
        val keyIdx = body.indexOf("\"content\"").takeIf { it >= 0 } ?: return null
        val open = body.indexOf('[', keyIdx).takeIf { it >= 0 } ?: return null
        var depth = 1; var i = open + 1; var inStr = false
        while (i < body.length && depth > 0) {
            val c = body[i]
            if (inStr) {
                if (c == '\\' && i + 1 < body.length) { i += 2; continue }
                if (c == '"') inStr = false
                i += 1; continue
            }
            when (c) {
                '"' -> inStr = true
                '[' -> depth += 1
                ']' -> depth -= 1
            }
            if (depth == 0) return body.substring(open + 1, i)
            i += 1
        }
        return null
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
