package j2keval

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoadExpectationsTest {

    @Test
    fun `parses minimal valid file`() {
        val tmp = Files.createTempFile("expectations", ".txt")
        tmp.writeText("""
            # comment
            01.kt | tag1 | yes | foo | description one
            01.kt | tag2 | no  | bar | description two
            02.kt | tag3 | yes | baz | description three
        """.trimIndent())
        val map = loadExpectations(tmp)
        assertEquals(2, map.size)
        assertEquals(2, map.getValue("01.kt").size)
        assertEquals("tag1", map.getValue("01.kt")[0].tag)
        assertEquals(true, map.getValue("01.kt")[0].shouldMatch)
        assertEquals(false, map.getValue("01.kt")[1].shouldMatch)
    }

    @Test
    fun `pipe inside description column is preserved (split limit=5)`() {
        val tmp = Files.createTempFile("expectations-pipe-desc", ".txt")
        // 5 columns total: path | tag | yes | regex | desc-with-pipe-chars
        // Without limit=5, the trailing pipes in the description would be
        // interpreted as more columns and the description would be cut at "left".
        tmp.writeText("01.kt | tag | yes | foo | left | middle | right\n")
        val map = loadExpectations(tmp)
        val e = map.getValue("01.kt").single()
        assertEquals("foo", e.pattern)
        assertEquals("left | middle | right", e.description)
    }

    @Test
    fun `missing file returns empty map without throwing`() {
        val map = loadExpectations(Files.createTempDirectory("expectations-missing").resolve("nope.txt"))
        assertTrue(map.isEmpty())
    }

    @Test
    fun `malformed lines (under 5 columns) are silently skipped`() {
        val tmp = Files.createTempFile("expectations-bad", ".txt")
        tmp.writeText("""
            01.kt | only | three | columns
            02.kt | tag | yes | regex | description
        """.trimIndent())
        val map = loadExpectations(tmp)
        assertEquals(1, map.size, "malformed line should be dropped, valid one kept")
        assertNotNull(map["02.kt"])
    }
}
