package j2keval

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.stmt.TryStmt
import java.nio.file.Path

// counts the same syntactic categories on the .java side that
// StructuralMetrics counts on the .kt side. ratio gives a recall signal
// per category -- did j2k drop any try-with-resources, anonymous classes,
// static finals, varargs, etc.
//
// without this pass the eval only sees what the converter emitted, not
// what it failed to convert. plain JavaParser, no symbol solver -- the
// kotlin side is syntactic too so resolving types here would just bloat
// the dep tree for no signal gain.
data class JavaMetrics(
    val file: Path,
    val locJava: Int,
    // true if JavaParser couldn't recover any tree at all. all counts are
    // zero in that case; without the flag, "parse failed" looks like
    // "no constructs" and silently skews recall.
    val parseFailed: Boolean,
    val tryWithResourceCount: Int,        // statements, not individual resources
    val resourceCount: Int,                // resources summed across all statements
    val anonymousClassExprs: Int,
    val staticFinalFields: Int,
    val staticFinalLiteralFields: Int,    // subset where the RHS is a literal
    // separate counter: static final fields whose RHS is a compile-time
    // constant EXPRESSION (per JLS 15.28) but not a single literal -- e.g.
    // `1 + 2`, `"a" + "b"`. these are const-eligible in Kotlin but the
    // literal-only check undercounts. JavaParser doesn't fully evaluate
    // 15.28; this is a syntactic shortcut: BinaryExpr where both operands
    // resolve to literal/literal-string forms.
    val staticFinalConstExprFields: Int,
    val varargParameters: Int,
    val innerClassDecls: Int,             // non-static nested classes
    val singleAbstractMethodInterfaces: Int,  // `fun interface` candidates
)

object JavaScan {

    // BLEEDING_EDGE so the parser keeps a partial AST when individual nodes
    // fail. newj2k/projections.java is missing a semicolon on purpose (j2k
    // testData -- they verify the converter handles broken java). under the
    // permissive config we still get usable counts off the rest of the file.
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
        // ParseResult can carry a partial AST even when the source has syntax
        // errors -- use it whenever it's present. only fall through to
        // parseFailed=true if we get nothing back at all.
        val result = runCatching { parser.parse(wrapped) }.getOrNull()
        val cu = result?.result?.orElse(null) ?: run {
            System.err.println("[eval] JavaParser failed to parse $file; recall counts marked parse_failed=true")
            return JavaMetrics(
                file = file, locJava = text.count { it == '\n' } + 1, parseFailed = true,
                tryWithResourceCount = 0, resourceCount = 0,
                anonymousClassExprs = 0, staticFinalFields = 0, staticFinalLiteralFields = 0,
                staticFinalConstExprFields = 0,
                varargParameters = 0, innerClassDecls = 0, singleAbstractMethodInterfaces = 0,
            )
        }
        // result.isSuccessful() can be false when problems were reported even
        // though the partial tree is usable. leaving parseFailed=false there
        // -- findAll() still returns useful numbers off the recoverable parts.
        // caveat for whoever reads this later: a syntactically broken .java
        // can land here with parseFailed=false. the recall ratio will reflect
        // the partial tree (i.e. could undercount). projections.java is the
        // only fixture this hits today.

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
        // const-expression detection: BinaryExpr where every operand is a
        // literal (recursively). catches `1 + 2`, `"a" + "b"`, `60 * 60`.
        // misses references to other const fields (`X + 1` where X is also
        // static final) and unary expressions on literals (`-1` already lands
        // as IntegerLiteralExpr.minus, not UnaryExpr); good enough to signal
        // "literal-only undercounts what const val could promote."
        val staticFinalConstExpr = fields.sumOf { fd ->
            fd.variables.count { v ->
                val init = v.initializer.orElse(null) ?: return@count false
                if (init.isLiteralExpr || init.isBooleanLiteralExpr || init.isStringLiteralExpr ||
                    init.isIntegerLiteralExpr || init.isLongLiteralExpr ||
                    init.isCharLiteralExpr || init.isDoubleLiteralExpr) return@count false
                isLiteralOnlyExpression(init)
            }
        }
        val varargs = cu.findAll(MethodDeclaration::class.java)
            .flatMap { it.parameters }
            .count { it.isVarArgs }
        val innerClasses = cu.findAll(ClassOrInterfaceDeclaration::class.java)
            .count { it.isInnerClass }
        // SAM = exactly one abstract method. default and static methods don't
        // count as abstract, matching the standard SAM definition.
        //
        // TODO: this counts only methods declared on the interface itself.
        // JLS 9.8 says a SAM type can inherit its abstract method from a
        // super-interface (e.g. interface Foo extends Bar, where Bar carries
        // the single abstract method). The current corpus does not exercise
        // that case (every newJ2k SAM fixture declares its own method), so
        // shipping the simpler check + a fixture that documents the gap is
        // honest. See `edge-cases/16_inherited_sam/` for the test case.
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
            staticFinalConstExprFields = staticFinalConstExpr,
            varargParameters = varargs,
            innerClassDecls = innerClasses,
            singleAbstractMethodInterfaces = samInterfaces,
        )
    }

    /**
     * Recursive check for "every leaf is a literal." returns false on any
     * NameExpr / FieldAccessExpr -- those reference other symbols whose
     * const-ness we can't know without symbol resolution.
     */
    private fun isLiteralOnlyExpression(expr: com.github.javaparser.ast.expr.Expression): Boolean {
        if (expr.isLiteralExpr || expr.isStringLiteralExpr || expr.isBooleanLiteralExpr ||
            expr.isIntegerLiteralExpr || expr.isLongLiteralExpr ||
            expr.isCharLiteralExpr || expr.isDoubleLiteralExpr) return true
        if (expr.isBinaryExpr) {
            val be = expr.asBinaryExpr()
            return isLiteralOnlyExpression(be.left) && isLiteralOnlyExpression(be.right)
        }
        if (expr.isEnclosedExpr) return isLiteralOnlyExpression(expr.asEnclosedExpr().inner)
        if (expr.isUnaryExpr) return isLiteralOnlyExpression(expr.asUnaryExpr().expression)
        return false
    }

    // newj2k has a few fragment-style fixtures (a single method declaration,
    // no enclosing class -- the varArg/* ones). they aren't valid CUs so
    // we wrap them in a synthetic class before parsing.
    private fun looksLikeFragment(text: String): Boolean {
        // strip line comments, then check for any top-level decl keyword.
        // cheap regex, fine for the fixture shapes we actually hit.
        val noLineComments = text.replace(Regex("(?m)^\\s*//.*$"), "")
        return Regex("""\b(class|interface|enum|record)\s+\w""").containsMatchIn(noLineComments).not()
    }
}
