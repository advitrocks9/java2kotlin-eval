package j2keval

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstValFixTest {

    @Test
    fun `top-level int literal is promoted`() {
        val src = "val RETRY_LIMIT = 3\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals("const val RETRY_LIMIT = 3\n", out)
    }

    @Test
    fun `string literal at top level is promoted`() {
        val src = """val BASE_PATH = "/api/v1"""" + "\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals(true, out.contains("const val BASE_PATH"))
    }

    @Test
    fun `val inside companion object is promoted`() {
        val src = """
            class Sample {
                companion object {
                    val TIMEOUT_MS = 5000L
                    val DEBUG = false
                }
            }
        """.trimIndent() + "\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(2, n)
    }

    @Test
    fun `val inside method body is NOT promoted`() {
        val src = """
            fun foo() {
                val x = 7
                println(x)
            }
        """.trimIndent() + "\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(0, n)
    }

    @Test
    fun `val inside non-companion class scope is NOT promoted`() {
        // a regular class body is not a const-val target -- const val only works
        // top-level, in object, or in companion object.
        val src = """
            class Box {
                val limit = 42
            }
        """.trimIndent() + "\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(0, n)
    }

    @Test
    fun `val with computed RHS is left alone (conservative)`() {
        // 1 + 2 IS const-eligible in Kotlin, but my regex is RHS-literal only.
        // I prefer false negatives to false positives here.
        val src = "val COMPUTED = 1 + 2\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(0, n)
    }

    @Test
    fun `already-const val is not double-promoted`() {
        val src = "const val FOO = 1\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(0, n)
        assertEquals(src, out)
    }
}
