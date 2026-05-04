package j2keval

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lock the lateinit-counting metric. J2K reaches for `lateinit var` when a
 * non-null field can't be initialised at construction; without this counter
 * the eval is blind to that escape hatch and a high-`!!`/low-`lateinit`
 * profile reads the same as a low-`!!`/high-`lateinit` profile, even though
 * one is "the converter chose runtime-checked deferred init" and the other
 * is "the converter chose call-site assertions."
 */
class MetricsLateinitTest {

    @Test
    fun `bare lateinit var counts`() {
        val src = "lateinit var name: String\n"
        assertEquals(1, scan(src).lateinitVars)
    }

    @Test
    fun `lateinit with leading visibility modifier counts`() {
        val src = """
            class C {
                private lateinit var name: String
                internal lateinit var other: String
                public lateinit var third: String
            }
        """.trimIndent() + "\n"
        assertEquals(3, scan(src).lateinitVars)
    }

    @Test
    fun `lateinit with trailing visibility (J2K's flipped order) counts`() {
        // J2K sometimes emits `lateinit private var` rather than the more
        // common `private lateinit var`. Both are valid Kotlin.
        val src = "lateinit private var x: Int\n"
        assertEquals(1, scan(src).lateinitVars)
    }

    @Test
    fun `plain val should not be counted`() {
        val src = "val x: String = \"hi\"\nvar y: Int = 0\n"
        assertEquals(0, scan(src).lateinitVars)
    }

    @Test
    fun `the substring 'lateinit' inside a string literal does not count`() {
        // The regex requires a `var` keyword adjacent to `lateinit`, so a
        // mention in a string or KDoc shouldn't double-fire. Worth a test
        // because the regex is the cheapest path; PSI already filters
        // tokens correctly.
        val src = "val msg = \"call lateinit init\"\nvar lateinitDescription = \"text\"\n"
        assertEquals(0, scan(src).lateinitVars)
    }

    private fun scan(src: String): StructuralMetrics {
        val tmp = createTempFile("metrics-lateinit-", ".kt")
        tmp.writeText(src)
        return Metrics.scan(tmp)
    }
}
