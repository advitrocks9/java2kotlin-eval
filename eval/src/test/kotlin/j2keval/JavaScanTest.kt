package j2keval

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JavaScanTest {

    @Test
    fun `counts try-with-resources statements and resources separately`() {
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            import java.io.*;
            class C {
                void single() throws IOException {
                    try (InputStream a = new ByteArrayInputStream(new byte[1])) { a.read(); }
                }
                void multi() throws IOException {
                    try (InputStream a = new ByteArrayInputStream(new byte[1]);
                         InputStream b = new ByteArrayInputStream(new byte[1])) {
                        a.read(); b.read();
                    }
                }
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        assertEquals(2, m.tryWithResourceCount)
        assertEquals(3, m.resourceCount, "single + multi(2) = 3 total resources")
    }

    @Test
    fun `counts anonymous class expressions`() {
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            class C {
                Runnable r = new Runnable() { public void run() {} };
                Object x = new Object() { @Override public String toString() { return ""; } };
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        assertEquals(2, m.anonymousClassExprs)
    }

    @Test
    fun `counts static final fields and the literal-RHS subset separately`() {
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            class C {
                static final int LITERAL = 7;
                static final String LITSTR = "x";
                static final int COMPUTED = compute();
                static final int[] ARR = {1, 2};
                static int compute() { return 0; }
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        assertEquals(4, m.staticFinalFields)
        assertEquals(2, m.staticFinalLiteralFields, "LITERAL and LITSTR are literal-RHS; the others aren't")
    }

    @Test
    fun `varargs counted across method declarations`() {
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            class C {
                void a(Object... os) {}
                void b(int x, String... ss) {}
                void c(int x) {}
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        assertEquals(2, m.varargParameters)
    }

    @Test
    fun `single-abstract-method interface vs default-method interface`() {
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            class Wrap {
                @FunctionalInterface
                interface SAM { void run(); }
                interface NotSAM { void a(); void b(); }
                interface OneAbstractWithDefault { void a(); default void b() {} }
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        // SAM and OneAbstractWithDefault both have exactly one abstract method.
        assertEquals(2, m.singleAbstractMethodInterfaces)
    }

    @Test
    fun `static final with binary const expression is counted as expression-RHS`() {
        // 1+2 and "a"+"b" are JLS 15.28 constant expressions; const-eligible
        // in Kotlin. previously the literal-only check undercounted.
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            class C {
                static final int LITERAL = 7;
                static final int SUM = 1 + 2;
                static final String JOINED = "a" + "b";
                static final int CALLED = compute();
                static int compute() { return 0; }
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        assertEquals(1, m.staticFinalLiteralFields, "LITERAL only")
        assertEquals(2, m.staticFinalConstExprFields, "SUM + JOINED, not CALLED")
    }

    @Test
    fun `fragment-style fixtures (no enclosing class) are wrapped before parsing`() {
        val tmp = Files.createTempFile("scan", ".java")
        tmp.writeText("""
            //method
            int nya(Object... objs) {
                return objs.length;
            }
        """.trimIndent())
        val m = JavaScan.scan(tmp); assertNotNull(m)
        assertEquals(1, m.varargParameters)
    }
}
