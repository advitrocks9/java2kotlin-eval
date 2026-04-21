package j2keval

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path

/**
 * PSI-based metrics. Same shape as the regex pass in Metrics.kt but reading
 * the actual AST. Hits things regex was wrong about: !! inside string
 * interpolations isn't double-counted, `object :` literals are walked
 * properly even when nested, const-eligibility is decided by KtConstantExpression.
 */
data class PsiMetrics(
    val file: Path,
    val locKotlin: Int,
    val notNullAsserts: Int,
    val objectLiteralExprs: Int,    // anonymous-class-style object expressions
    val funInterfaces: Int,
    val constVals: Int,
    val plainVals: Int,
    val constEligibleVals: Int,
    val innerClasses: Int,
    val varargParams: Int,
)

object PsiScan {

    fun scan(file: Path, ktFile: KtFile): PsiMetrics {
        val v = Visitor()
        ktFile.accept(v)
        return PsiMetrics(
            file = file,
            locKotlin = ktFile.text.count { it == '\n' } + 1,
            notNullAsserts = v.notNullAsserts,
            objectLiteralExprs = v.objectLiteralExprs,
            funInterfaces = v.funInterfaces,
            constVals = v.constVals,
            plainVals = v.plainVals,
            constEligibleVals = v.constEligibleVals,
            innerClasses = v.innerClasses,
            varargParams = v.varargParams,
        )
    }

    private class Visitor : KtTreeVisitorVoid() {
        var notNullAsserts = 0
        var objectLiteralExprs = 0
        var funInterfaces = 0
        var constVals = 0
        var plainVals = 0
        var constEligibleVals = 0
        var innerClasses = 0
        var varargParams = 0

        override fun visitPostfixExpression(expr: KtPostfixExpression) {
            if (expr.operationToken == KtTokens.EXCLEXCL) notNullAsserts += 1
            super.visitPostfixExpression(expr)
        }

        override fun visitObjectLiteralExpression(expr: KtObjectLiteralExpression) {
            objectLiteralExprs += 1
            super.visitObjectLiteralExpression(expr)
        }

        override fun visitClass(klass: KtClass) {
            if (klass.hasModifier(KtTokens.FUN_KEYWORD) && klass.isInterface()) {
                funInterfaces += 1
            }
            if (klass.hasModifier(KtTokens.INNER_KEYWORD)) {
                innerClasses += 1
            }
            super.visitClass(klass)
        }

        override fun visitParameter(parameter: KtParameter) {
            if (parameter.hasModifier(KtTokens.VARARG_KEYWORD)) varargParams += 1
            super.visitParameter(parameter)
        }

        override fun visitProperty(property: KtProperty) {
            if (!property.isVar) {
                if (property.hasModifier(KtTokens.CONST_KEYWORD)) {
                    constVals += 1
                } else {
                    plainVals += 1
                    if (looksConstEligible(property)) constEligibleVals += 1
                }
            }
            super.visitProperty(property)
        }

        private fun looksConstEligible(property: KtProperty): Boolean {
            // const val rules: top-level OR in a `companion object` / `object`,
            // RHS is a primitive literal or string literal with no template.
            if (property.isLocal) return false
            val parent = property.parent
            val parentObj = (parent as? KtClassOrObject) ?: return parent is KtFile
            val isObjectish = parentObj is KtObjectDeclaration
            if (!isObjectish) return false
            val rhs = property.initializer ?: return false
            return when (rhs) {
                is KtConstantExpression -> true
                is KtStringTemplateExpression -> rhs.entries.all { it.expression == null }
                else -> false
            }
        }
    }
}
