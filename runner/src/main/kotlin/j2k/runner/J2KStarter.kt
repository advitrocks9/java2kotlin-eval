package j2k.runner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Headless static-j2k. Invoked as:
 *   ./gradlew runIde --args="j2k <input-dir> <output-dir>"
 *
 * Same pattern Meta describe for Kotlinator: ApplicationStarter drives the same
 * JavaToKotlinAction.Handler that the IDE's Convert action calls. Going through
 * the action (not the lower-level converter) keeps post-processing identical to
 * what users see in the IDE -- which is exactly what we want to evaluate.
 */
@Suppress("UnstableApiUsage")
class J2KStarter : ApplicationStarter {
    override val commandName: String = "j2k"

    override fun main(args: List<String>) {
        // args[0] is the command name itself
        if (args.size < 3) {
            System.err.println("usage: j2k <input-dir> <output-dir>")
            exitProcess(2)
        }

        val inDir = Path.of(args[1]).toAbsolutePath()
        val outDir = Path.of(args[2]).toAbsolutePath()
        Files.createDirectories(outDir)

        val javaFiles = Files.walk(inDir).use { stream ->
            stream.filter { it.toString().endsWith(".java") }.toList()
        }
        if (javaFiles.isEmpty()) {
            System.err.println("no .java files under $inDir")
            exitProcess(1)
        }

        // Open the input directory as a project so PSI has somewhere to live.
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.loadAndOpenProject(inDir.toString())
            ?: error("could not open project at $inDir")

        try {
            val psiManager = PsiManager.getInstance(project)
            val vfs = LocalFileSystem.getInstance()

            val virtualFiles: List<VirtualFile> = javaFiles.mapNotNull {
                vfs.refreshAndFindFileByIoFile(it.toFile())
            }

            // The action's Handler is the same code path the IDE uses.
            val psiFiles: List<PsiJavaFile> = virtualFiles
                .mapNotNull { psiManager.findFile(it) }
                .filterIsInstance<PsiJavaFile>()

            println("[j2k] converting ${psiFiles.size} files")

            val module = ModuleManager.getInstance(project).modules.firstOrNull()
                ?: error("project at $inDir has no modules - cannot run J2K without one")

            val converted = JavaToKotlinAction.Handler.convertFiles(
                /* javaFiles = */ psiFiles,
                /* project = */ project,
                /* module = */ module,
                /* enableExternalCodeProcessing = */ false,
                /* askExternalCodeProcessing = */ false,
                /* forceUsingOldJ2k = */ false,
            )

            // Mirror the input layout into outDir, named *.kt.
            WriteCommandAction.runWriteCommandAction(project) {
                converted.forEach { kt ->
                    val srcVf = kt.virtualFile ?: return@forEach
                    val rel = srcVf.path.removePrefix(inDir.toString()).removePrefix("/")
                    val dst = outDir.resolve(rel.replaceAfterLast('.', "kt"))
                    Files.createDirectories(dst.parent)
                    Files.writeString(dst, kt.text)
                }
            }

            println("[j2k] wrote ${converted.size} files to $outDir")
        } finally {
            ApplicationManager.getApplication().invokeAndWait {
                projectManager.closeAndDispose(project)
            }
            // ApplicationStarter does not call System.exit; we do.
            exitProcess(0)
        }
    }
}
