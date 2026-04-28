package j2keval

// kotlin-compiler-embeddable shades com.intellij under
// org.jetbrains.kotlin.com.intellij.* so the compiler ships standalone.
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Single-file Kotlin PSI parser. KotlinCoreEnvironment is the lightest setup
 * I could find that gives a real KtFile (with `fun interface` decls,
 * `companion object` blocks, `object :` expressions, etc) without dragging
 * in the whole IntelliJ Platform. The Disposable lives for the lifetime of
 * the process; for a small batch eval that's fine. For a long-lived service
 * I'd reuse the env across files.
 */
class PsiEnv(private val parentDisposable: Disposable = Disposer.newDisposable("j2keval-psi")) {

    private val env: KotlinCoreEnvironment

    init {
        val cfg = CompilerConfiguration().apply {
            put(
                CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false)
            )
            put(CommonConfigurationKeys.MODULE_NAME, "j2keval")
        }
        env = KotlinCoreEnvironment.createForProduction(
            parentDisposable,
            cfg,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    fun parse(file: Path): KtFile {
        val text = file.readText()
        val psiFile = PsiFileFactory.getInstance(env.project)
            .createFileFromText(file.fileName.toString(), KotlinLanguage.INSTANCE, text)
        return psiFile as KtFile
    }

    fun close() = Disposer.dispose(parentDisposable)
}
