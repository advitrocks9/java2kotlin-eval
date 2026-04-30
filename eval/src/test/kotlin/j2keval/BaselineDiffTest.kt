package j2keval

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineDiffTest {

    @Test
    fun `self-diff is identical, no hunks emitted`() {
        val tmp = Files.createTempDirectory("baseline-self-")
        val candidate = tmp.resolve("a/Foo.kt").also { it.parent.toFile().mkdirs() }
        val baseline = tmp.resolve("b/Foo.kt").also { it.parent.toFile().mkdirs() }
        val text = """
            class Foo {
                val x = 1
            }
        """.trimIndent()
        candidate.writeText(text)
        baseline.writeText(text)

        val results = BaselineDiff.compareCorpus(tmp.resolve("a"), tmp.resolve("b"))
        assertEquals(1, results.size)
        val r = results[0]
        assertTrue(r.identical, "self-diff should be identical")
        assertEquals(0, r.deltaCount)
        assertNull(r.unifiedDiff)
        assertFalse(r.baselineMissing)
    }

    @Test
    fun `whitespace-only differences are normalized away`() {
        val tmp = Files.createTempDirectory("baseline-ws-")
        val candidate = tmp.resolve("a/Foo.kt").also { it.parent.toFile().mkdirs() }
        val baseline = tmp.resolve("b/Foo.kt").also { it.parent.toFile().mkdirs() }
        candidate.writeText("class Foo {\n\n    val x = 1\n}\n")
        baseline.writeText("class Foo {\n\n\n\n    val x = 1   \n}\n\n\n")

        val results = BaselineDiff.compareCorpus(tmp.resolve("a"), tmp.resolve("b"))
        assertTrue(results[0].identical, "trailing-ws and blank-line runs should normalize away")
    }

    @Test
    fun `real semantic drift surfaces as deltas with a unified diff`() {
        val tmp = Files.createTempDirectory("baseline-drift-")
        val candidate = tmp.resolve("a/Foo.kt").also { it.parent.toFile().mkdirs() }
        val baseline = tmp.resolve("b/Foo.kt").also { it.parent.toFile().mkdirs() }
        candidate.writeText("class Foo {\n    val x: Int = 1\n}\n")
        baseline.writeText("class Foo {\n    const val x: Int = 1\n}\n")

        val results = BaselineDiff.compareCorpus(tmp.resolve("a"), tmp.resolve("b"))
        val r = results[0]
        assertFalse(r.identical)
        assertTrue(r.deltaCount > 0)
        val ud = r.unifiedDiff
        assertTrue(ud != null && "const val" in ud, "unified diff should contain the divergent token")
    }

    @Test
    fun `baseline missing is surfaced separately from drift`() {
        val tmp = Files.createTempDirectory("baseline-missing-")
        val candidate = tmp.resolve("a/Foo.kt").also { it.parent.toFile().mkdirs() }
        candidate.writeText("class Foo\n")
        Files.createDirectories(tmp.resolve("b"))   // baseline corpus exists, file inside doesn't

        val results = BaselineDiff.compareCorpus(tmp.resolve("a"), tmp.resolve("b"))
        val r = results[0]
        assertTrue(r.baselineMissing)
        assertFalse(r.identical)
        assertEquals(0, r.deltaCount)
    }

    @Test
    fun `pairs by relative path so directory layout matches`() {
        val tmp = Files.createTempDirectory("baseline-rel-")
        val candidate = tmp.resolve("a/nested/dir/Foo.kt").also { it.parent.toFile().mkdirs() }
        val baseline = tmp.resolve("b/nested/dir/Foo.kt").also { it.parent.toFile().mkdirs() }
        candidate.writeText("class Foo\n")
        baseline.writeText("class Foo\n")

        val results = BaselineDiff.compareCorpus(tmp.resolve("a"), tmp.resolve("b"))
        assertEquals("nested/dir/Foo.kt", results[0].file)
        assertTrue(results[0].identical)
    }

    private fun Path.writeText(s: String) = this.writeText(s, Charsets.UTF_8)
}
