package j2keval

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.stmt.TryStmt
import java.nio.file.Path

/**
 * Input-side metrics over the .java files J2K is converting. Pairs with
 * StructuralMetrics/PsiMetrics on the Kotlin side to give a recall signal:
 * "did J2K produce a Kotlin construct for every Java construct that
 * should have one?"
 *
 * Without an input-side pass, the eval can only measure what the converter
 * emitted, not what it dropped. That's the recall-metric bullet from the
 * README's "what I'd want to do next" list.
 *
 * JavaParser, no symbol solver. The metrics are syntactic on the Kotlin
 * side too -- adding semantic resolution here would inflate the dep tree
 * for no signal gain.
 */
data class JavaMetrics(
    val file: Path,
    val locJava: Int,
    /** True if JavaParser failed to parse the file. All count fields are
     *  zero when this is true; without the flag, "scanner failed" looks
     *  identical to "file had zero constructs" and silently skews recall. */
    val parseFailed: Boolean,
    val tryWithResourceCount: Int,        // statements, not resources
    val resourceCount: Int,                // sum of resources across statements
    val anonymousClassExprs: Int,
    val staticFinalFields: Int,
    val staticFinalLiteralFields: Int,    // RHS is a primitive/string literal
    val varargParameters: Int,
    val innerClassDecls: Int,             // non-static nested classes
    val singleAbstractMethodInterfaces: Int,  // candidates for `fun interface`
)

object JavaScan {

    /**
     * Permissive parser. The newj2k testData deliberately includes invalid
     * Java (e.g. projections.java is missing a semicolon to test J2K's
     * resilience against parse errors). We still want best-effort syntactic
     * counts off whatever JavaParser CAN recover. The parser keeps a
     * partial AST when individual nodes fail, so findAll() still returns
     * useful numbers as long as the overall ParseResult has any result
     * tree at all.
     */
    private val parser = JavaParser(
        ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
    )

    fun scan(file: Path): JavaMetrics? {
        val text = runCatching { file.toFile().readText() }.getOrNull() ?: return null
        // Some newj2k fixtures are fragment-style (method body or single
        // declaration with no enclosing class). Wrap them in a synthetic
        // class before parsing so the same findAll() walk works on both.
        val wrapped = if (looksLikeFragment(text)) {
            "class __J2KEvalWrap__ {\n$text\n}\n"
        } else {
            text
        }
        // parser.parse returns a ParseResult that may have a partial AST
        // even when the source has syntax errors. Take it whenever it's
        // present; only fall through to parse_failed=true if we can't
        // recover any tree at all.
        val result = runCatching { parser.parse(wrapped) }.getOrNull()
        val cu = result?.result?.orElse(null) ?: run {
            System.err.println("[eval] JavaParser failed to parse $file; recall counts marked parse_failed=true")
            return JavaMetrics(
                file = file, locJava = text.count { it == '\n' } + 1, parseFailed = true,
                tryWithResourceCount = 0, resourceCount = 0,
                anonymousClassExprs = 0, staticFinalFields = 0, staticFinalLiteralFields = 0,
                varargParameters = 0, innerClassDecls = 0, singleAbstractMethodInterfaces = 0,
            )
        }
        // result.isSuccessful is false when problems were reported; the AST
        // is still usable but we surface the partial state in the JSONL so
        // a reviewer can see "scan ran but on a damaged tree". For now we
        // leave parseFailed=false in that case since findAll() still works.

        val tryWithRes = cu.findAll(TryStmt::class.java).filter { it.resources.isNotEmpty() }
        val resCount = tryWithRes.sumOf { it.resources.size }
        val anonExprs = cu.findAll(ObjectCreationExpr::class.java).count { it.anonymousClassBody.isPresent }
        val fields = cu.findAll(FieldDeclaration::class.java).filter { it.isStatic && it.isFinal }
        val staticFinal = fields.sumOf { it.variables.size }
        val staticFinalLiteral = fields.sumOf { fd ->
            fd.variables.count { v ->
                val init = v.initializer.orElse(null) ?: return@count false
                init.isLiteralExpr || init.isBooleanLiteralExpr || init.isStringLiteralExpr ||
                        init.isIntegerLiteralExpr || init.isLongLiteralExpr ||
                        init.isCharLiteralExpr || init.isDoubleLiteralExpr
            }
        }
        val varargs = cu.findAll(MethodDeclaration::class.java)
            .flatMap { it.parameters }
            .count { it.isVarArgs }
        val innerClasses = cu.findAll(ClassOrInterfaceDeclaration::class.java)
            .count { it.isInnerClass }
        // Single-abstract-method interface: nominal `fun interface` candidate.
        // Counts default methods as concrete (not abstract), matching the
        // SAM definition.
        val samInterfaces = cu.findAll(ClassOrInterfaceDeclaration::class.java)
            .filter { it.isInterface }
            .count { iface ->
                val abstract = iface.methods.count { m -> !m.isDefault && !m.isStatic && m.body.isEmpty }
                abstract == 1
            }

        return JavaMetrics(
            file = file,
            locJava = text.count { it == '\n' } + 1,
            parseFailed = false,
            tryWithResourceCount = tryWithRes.size,
            resourceCount = resCount,
            anonymousClassExprs = anonExprs,
            staticFinalFields = staticFinal,
            staticFinalLiteralFields = staticFinalLiteral,
            varargParameters = varargs,
            innerClassDecls = innerClasses,
            singleAbstractMethodInterfaces = samInterfaces,
        )
    }

    /**
     * Heuristic: detect newj2k fragment-style fixtures (method body or single
     * declaration with no enclosing class/interface/enum). The newj2k testData
     * has a few of these in `varArg/`, formatted as a `//method` comment plus
     * a method declaration. They aren't valid Java compilation units so we
     * wrap them.
     */
    private fun looksLikeFragment(text: String): Boolean {
        // Strip line comments and check whether the file ever opens a top-level
        // class/interface/enum/record. Cheap regex; good enough for the fixture
        // shapes we hit.
        val noLineComments = text.replace(Regex("(?m)^\\s*//.*$"), "")
        return Regex("""\b(class|interface|enum|record)\s+\w""").containsMatchIn(noLineComments).not()
    }
}
