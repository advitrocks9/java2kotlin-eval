package j2k.runner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.project.Project
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.writeText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.system.exitProcess

/**
 * Headless static-j2k. Same architecture Meta used for Kotlinator: an
 * ApplicationStarter calling into JavaToKotlinAction.Handler. The wrinkle
 * (and the part their post glosses over) is that you can't just open a
 * directory and call convertFiles -- the auto-opened project has no JDK,
 * the converter's elementsToKotlin call sees `targetKaModule == null`,
 * and it returns Result.EMPTY without writing anything. We attach a JDK
 * and a Java source root before invoking.
 *
 * usage:  ./gradlew :runner:runIde --args="j2k <input-dir> <output-dir>"
 */
@Suppress("UnstableApiUsage")
class J2KStarter : ApplicationStarter {
    override val commandName: String = "j2k"

    override fun main(args: List<String>) {
        try {
            runConversion(args)
            exitProcess(0)
        } catch (t: Throwable) {
            // ApplicationStarter swallows uncaught throwables, which is how I
            // spent an evening looking at empty output dirs. Catch and print.
            System.err.println("[j2k] FATAL: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace(System.err)
            exitProcess(1)
        }
    }

    private fun runConversion(args: List<String>) {
        if (args.size < 3) error("usage: j2k <input-dir> <output-dir>")
        val inDir = Path.of(args[1]).toAbsolutePath().normalize()
        val outDir = Path.of(args[2]).toAbsolutePath().normalize()
        require(inDir.exists()) { "input dir does not exist: $inDir" }
        outDir.createDirectories()

        // Stage the input under a fresh project directory so we can stamp
        // .idea/iml files without touching the user's tree. Copy is cheap
        // for jcommander-sized repos (a few hundred files).
        val workRoot = Files.createTempDirectory("j2k-work-")
        val srcRoot = workRoot.resolve("src")
        srcRoot.createDirectories()
        copyJavaTree(inDir, srcRoot)

        val javaPaths = collectJavaFiles(srcRoot)
        if (javaPaths.isEmpty()) error("no .java files under $inDir")
        log("staged ${javaPaths.size} java files under $workRoot")

        // ApplicationStarter.main runs on EDT under a write-intent read
        // context in 2024.3, so we don't need to invokeAndWait around the
        // openProject / write actions -- doing so deadlocks on the EDT.
        // ProjectManagerEx is the modern replacement for the deprecated
        // ProjectManager.loadAndOpenProject (which silently returns the
        // already-open project in headless and fails the second time).
        val pmEx = ProjectManagerEx.getInstanceEx()
        // forceOpenInNewFrame = false is critical in headless: setting it true
        // makes openProject wait for a Swing frame that never gets attached.
        // isNewProject = true skips the "load existing .idea/" path, which
        // wants a workspace.xml that we don't ship.
        val task = OpenProjectTask {
            forceOpenInNewFrame = false
            isNewProject = true
            useDefaultProjectAsTemplate = false
            runConfigurators = false
        }
        val project: Project = pmEx.openProject(workRoot, task)
            ?: error("could not open project at $workRoot")
        log("opened project: ${project.name}")

        try {
            attachJdkAndSrcRoot(project, srcRoot)
            log("attached JDK + source root")
            val converted = doConvert(project, srcRoot)
            log("converter produced ${converted.size} files")
            mirrorOutputs(converted, srcRoot, outDir)
        } finally {
            pmEx.closeAndDispose(project)
        }
    }

    private fun copyJavaTree(from: Path, to: Path) {
        Files.walk(from).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".java") }
                .forEach { src ->
                    val rel = from.relativize(src)
                    val dst = to.resolve(rel.toString())
                    dst.parent?.createDirectories()
                    Files.copy(src, dst)
                }
        }
    }

    private fun collectJavaFiles(root: Path): List<Path> =
        Files.walk(root).use { it.filter { p -> p.toString().endsWith(".java") }.toList() }

    private fun attachJdkAndSrcRoot(project: Project, srcRoot: Path) {
        // JDK creation indexes the JDK class roots -- a write op. We're
        // already on EDT inside a write-intent context; runWriteAction
        // upgrades that.
        val sdk: Sdk = runWriteAction {
            val table = ProjectJdkTable.getInstance()
            table.findJdk("auto-jdk-21") ?: run {
                val javaHome = System.getProperty("java.home") ?: error("java.home unset")
                val sdk = JavaSdk.getInstance().createJdk("auto-jdk-21", javaHome, false)
                table.addJdk(sdk)
                sdk
            }
        }
        runWriteAction {
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }

        val module = ensureModule(project, srcRoot)

        val srcVf: VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(srcRoot)
            ?: error("no VFS entry for $srcRoot")

        runWriteAction {
            val rootModel: ModifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
            rootModel.contentEntries.forEach { rootModel.removeContentEntry(it) }
            val ce = rootModel.addContentEntry(srcVf)
            ce.addSourceFolder(srcVf, /* isTestSource = */ false)
            rootModel.inheritSdk()
            rootModel.commit()
        }

        VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ true, /* reloadChildren = */ true, srcVf)
    }

    /**
     * loadAndOpenProject opens the directory but doesn't auto-create a
     * module unless a .iml is already present. We create one in-memory so
     * J2K has something to attach the source root to.
     */
    private fun ensureModule(project: Project, srcRoot: Path): Module {
        val mm = ModuleManager.getInstance(project)
        mm.modules.firstOrNull()?.let { return it }
        log("creating in-memory module")
        return runWriteAction {
            val mmm: ModifiableModuleModel = mm.getModifiableModel()
            val imlPath = srcRoot.parent.resolve("module.iml").toString()
            val javaModuleType = ModuleTypeManager.getInstance().findByID("JAVA_MODULE")
            val mod = mmm.newModule(imlPath, javaModuleType.id)
            mmm.commit()
            mod
        }
    }

    private fun doConvert(project: Project, srcRoot: Path): Map<Path, String> {
        val files = ApplicationManager.getApplication().runReadAction<List<PsiJavaFile>> {
            val pm = PsiManager.getInstance(project)
            val vfs = LocalFileSystem.getInstance()
            collectJavaFiles(srcRoot)
                .mapNotNull { vfs.refreshAndFindFileByNioFile(it) }
                .mapNotNull { pm.findFile(it) as? PsiJavaFile }
        }
        log("found ${files.size} PSI java files")

        if (files.isEmpty()) return emptyMap()

        val module = ModuleManager.getInstance(project).modules.first()

        // commit any pending PSI / document changes before handing to J2K
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // We deliberately don't go through JavaToKotlinAction.Handler --
        // that call hits withModalProgress which spins waiting for a UI
        // dialog that doesn't exist in headless. NewJavaToKotlinConverter's
        // elementsToKotlin is synchronous, but it uses the Kotlin Analysis
        // API which throws ProhibitedAnalysisException if called from EDT.
        // Dispatch it to a pooled worker thread and block on the result.
        val converter = NewJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
        // J2K's nullability inferrer walks PSI, which needs a read action.
        // The Analysis API also refuses to run on EDT. Pool thread + read
        // action is the combination that satisfies both.
        val result = ApplicationManager.getApplication()
            .executeOnPooledThread<org.jetbrains.kotlin.j2k.Result> {
                ApplicationManager.getApplication().runReadAction<org.jetbrains.kotlin.j2k.Result> {
                    converter.elementsToKotlin(files)
                }
            }
            .get()

        // result.results[i] aligns with files[i]. Each ElementResult has
        // a `.text` field with the converted Kotlin source.
        return files.zip(result.results).mapNotNull { (java, r) ->
            val text = r?.text ?: return@mapNotNull null
            java.virtualFile.toNioPath().toAbsolutePath().normalize() to text
        }.toMap()
    }

    private fun mirrorOutputs(converted: Map<Path, String>, srcRoot: Path, outDir: Path) {
        var written = 0
        for ((javaPath, ktText) in converted) {
            val rel = srcRoot.relativize(javaPath).toString().removeSuffix(".java")
            val dst = outDir.resolve("$rel.kt")
            dst.parent?.createDirectories()
            dst.writeText(stripJ2KMarkers(ktText))
            written += 1
        }
        log("wrote $written .kt files to $outDir")
    }

    /**
     * Bypassing JavaToKotlinAction.Handler skips the IDE post-processing pass
     * which normally cleans up J2K's internal symbol-resolution markers. We
     * strip them here so kotlinc can read the output: `/*@@hash@@*/Foo` is the
     * marker shape, and qualified `kotlin.Int` / `kotlin.Long` etc are
     * stdlib types the post-processor would short-name.
     */
    private fun stripJ2KMarkers(s: String): String {
        var out = s.replace(Regex("""/\*@@[a-z]+@@\*/"""), "")
        out = out.replace(Regex("""\bkotlin\.(Int|Long|Short|Byte|Float|Double|Boolean|Char|String|Unit|Any|IntArray|LongArray|BooleanArray|FloatArray|DoubleArray|CharArray|ByteArray|ShortArray)\b""")) {
            it.groupValues[1]
        }
        out = out.replace(Regex("""@kotlin\.Throws\b"""), "@Throws")
        return out
    }

    private fun log(s: String) = println("[j2k] $s")
}
