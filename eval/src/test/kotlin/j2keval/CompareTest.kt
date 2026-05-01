package j2keval

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

class CompareTest {

    @Test
    fun `compare cross-tabs compile outcomes and pairs by file`() {
        val tmp = Files.createTempDirectory("compare-")
        val a = tmp.resolve("a.jsonl")
        val b = tmp.resolve("b.jsonl")
        a.writeText("""
            {"schema_version":1,"corpus":"X","source":"j2k","file":"01.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[{"tag":"t","passed":true,"should_match":true,"pattern":"x","expectation":"e","sample":null}]}
            {"schema_version":1,"corpus":"X","source":"j2k","file":"02.kt","java_input":null,"compile":{"ok":false,"errors":["err"],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[]}
        """.trimIndent() + "\n")
        b.writeText("""
            {"schema_version":1,"corpus":"X","source":"claude-sonnet-4-6","file":"01.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[{"tag":"t","passed":false,"should_match":true,"pattern":"x","expectation":"e","sample":null}]}
            {"schema_version":1,"corpus":"X","source":"claude-sonnet-4-6","file":"02.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[]}
        """.trimIndent() + "\n")

        val out = tmp.resolve("compare.md")
        Compare.run(a, b, out)
        val text = out.readText()
        // Header
        assertTrue("source `j2k`" in text, "should call out source A label")
        assertTrue("source `claude-sonnet-4-6`" in text, "should call out source B label")
        // Cross-tab: 01 both compile, 02 only B
        assertTrue("| both compile | 1 |" in text)
        assertTrue("| only B (`claude-sonnet-4-6`) | 1 |" in text)
        // Per-file: 01 hyp 1/1 vs 0/1
        assertTrue("`01.kt` | yes | 1/1 | yes | 0/1" in text)
        assertTrue("`02.kt` | **no** | -" in text, "compile fail surfaces with bold no")
    }

    @Test
    fun `regex patterns inside string fields don't unbalance brace counting`() {
        val tmp = Files.createTempDirectory("compare-strings-")
        val a = tmp.resolve("a.jsonl")
        val b = tmp.resolve("b.jsonl")
        // Pattern field contains \\\\u007b (the JSON-escaped backslash + brace).
        // A naive parser counts that brace and trips the array boundary.
        a.writeText("""{"schema_version":1,"corpus":"X","source":"j2k","file":"x.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[{"tag":"t1","passed":true,"should_match":true,"pattern":"\\.use\\s*\\{","expectation":"e","sample":".use {"},{"tag":"t2","passed":false,"should_match":true,"pattern":"\\bvararg\\s+\\w","expectation":"e","sample":null}]}
        """.trimIndent() + "\n")
        b.writeText("""{"schema_version":1,"corpus":"X","source":"claude","file":"x.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[]}
        """.trimIndent() + "\n")

        val out = tmp.resolve("compare.md")
        Compare.run(a, b, out)
        val text = out.readText()
        assertTrue("`x.kt` | yes | 1/2 |" in text, "should count both hypotheses (1 passed, 1 failed) -- saw: ${text.lines().joinToString("\n")}")
    }

    @Test
    fun `unmatched files surface as 'missing' on the side they're absent from`() {
        val tmp = Files.createTempDirectory("compare-miss-")
        val a = tmp.resolve("a.jsonl")
        val b = tmp.resolve("b.jsonl")
        a.writeText("""{"schema_version":1,"corpus":"X","source":"j2k","file":"only-in-a.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[]}
        """.trimIndent() + "\n")
        b.writeText("""{"schema_version":1,"corpus":"X","source":"claude","file":"only-in-b.kt","java_input":null,"compile":{"ok":true,"errors":[],"duration_ms":0},"metrics_regex":{"loc":1,"not_null_asserts":0,"anon_objects":0,"fun_interface":0,"const_val":0,"plain_val":0,"const_eligible_val":0,"throws_annotations":0,"inner_class":0,"vararg":0,"use_blocks":0},"hypotheses":[]}
        """.trimIndent() + "\n")

        val out = tmp.resolve("compare.md")
        Compare.run(a, b, out)
        val text = out.readText()
        assertTrue("`only-in-a.kt` | yes | - | missing |" in text)
        assertTrue("`only-in-b.kt` | missing | n/a | yes | -" in text)
    }
}
