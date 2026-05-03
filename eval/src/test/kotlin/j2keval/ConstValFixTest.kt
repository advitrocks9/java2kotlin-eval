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

    @Test
    fun `private val with literal RHS is promoted`() {
        // visibility modifiers are legal on `const val`. previous regex
        // matched only bare `val`, missing `private val X = 1` style decls
        // J2K emits when the Java source field was private.
        val src = "private val MAX = 7\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals(true, out.contains("private const val MAX = 7"))
    }

    @Test
    fun `internal val with literal RHS is promoted`() {
        val src = "internal val NAME = \"abc\"\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals(true, out.contains("internal const val NAME"))
    }

    @Test
    fun `public val with literal RHS is promoted`() {
        val src = "public val FLAG = false\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals(true, out.contains("public const val FLAG"))
    }

    @Test
    fun `hex literal RHS is promoted`() {
        // 0xff / 0xFF / 0X1A all const-eligible; previous regex rejected them
        val src = "val MASK = 0xff\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(1, n)
    }

    @Test
    fun `binary literal RHS is promoted`() {
        val src = "val BITS = 0b1010\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(1, n)
    }

    @Test
    fun `exponent-form numeric literal RHS is promoted`() {
        // 1e9 is a Double; const val LONG = 1e9 is legal in Kotlin.
        val src = "val NS_PER_S = 1e9\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(1, n)
    }

    @Test
    fun `char literal with escape RHS is promoted`() {
        val src = "val NL = '\\n'\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals(true, out.contains("const val NL = '\\n'"))
    }

    @Test
    fun `val with trailing line comment is promoted`() {
        // a `// foo` after the literal RHS must not break the regex match.
        val src = """
            object Foo {
                val X = 1 // bar
            }
        """.trimIndent() + "\n"
        val (n, out) = ConstValFix.rewrite(src)
        assertEquals(1, n)
        assertEquals(true, out.contains("const val X = 1 // bar"))
    }

    @Test
    fun `braces inside string literals do not move scope`() {
        // a `"}{"`-shaped literal in the source previously confused the
        // brace counter into thinking we'd left the object body. without
        // string scrubbing the X here was scored as NOT_PROMOTABLE.
        val src = """
            object Foo {
                val msg = "}{"
                val X = 1
            }
        """.trimIndent() + "\n"
        val (n, out) = ConstValFix.rewrite(src)
        // msg has a string literal RHS, also promotable -> 2 promotions
        assertEquals(2, n, "expected msg + X = 2 promotions; got out=\n$out")
        assertEquals(true, out.contains("const val X = 1"))
        assertEquals(true, out.contains("const val msg = \"}{\""))
    }

    @Test
    fun `braces inside line comment do not move scope`() {
        val src = """
            object Foo {
                val Y = 2 // }{
                val X = 1
            }
        """.trimIndent() + "\n"
        val (n, _) = ConstValFix.rewrite(src)
        assertEquals(2, n)
    }
}
