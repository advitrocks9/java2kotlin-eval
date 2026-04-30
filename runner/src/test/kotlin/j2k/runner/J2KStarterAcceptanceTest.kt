package j2k.runner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Acceptance test for the headless runner. Boots a real IDE via gradlew
 * runIde on a one-file corpus and asserts:
 *   - exit code 0
 *   - at least one .kt file was produced
 *   - no `[j2k] FATAL` line in idea.log (the catch-all FATAL my plugin
 *     prints when it traps an uncaught throwable)
 *
 * What this test deliberately does NOT assert:
 *   - "no SEVERE in the log" -- IntelliJ Platform 2024.3 + Kotlin plugin
 *     emits SEVERE entries for ThreadingAssertions during normal J2K
 *     operation (read-access checks during nullability inference). They
 *     don't break the conversion.
 *
 * Local-only. CI is gated off because runIde under xvfb on ubuntu-latest
 * hangs in IntelliJ Platform's `preloadNonHeadlessServices` before our
 * ApplicationStarter dispatches -- documented in `docs/HEADLESS_J2K.md`.
 * On a fresh local sandbox the test does pass (5+ minutes, full IDE boot).
 *
 * Run locally:
 *   J2K_RUN_ACCEPTANCE=1 ./gradlew :runner:test --tests '*Acceptance*'
 */
@EnabledIfEnvironmentVariable(named = "J2K_RUN_ACCEPTANCE", matches = "1")
class J2KStarterAcceptanceTest {

    @Test
    fun `runner converts a one-file Java corpus and exits cleanly`() {
        val repoRoot = locateRepoRoot()
        val tmp = Files.createTempDirectory("j2k-accept-")
        val input = tmp.resolve("input")
        val output = tmp.resolve("output")
        input.createDirectories()
        output.createDirectories()
        input.resolve("Hello.java").writeText(
            """
            package accept;
            public class Hello {
                public static String greet() { return "hi"; }
            }
            """.trimIndent()
        )

        val proc = ProcessBuilder(
            "${repoRoot}/gradlew",
            ":runner:runIde",
            "--args=j2k ${input} ${output}",
            "--quiet",
        )
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()

        val captured = StringBuilder()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { captured.appendLine(it) }
        }
        val exited = proc.waitFor(10, TimeUnit.MINUTES)
        if (!exited) {
            proc.destroyForcibly()
            error("runIde did not exit within 10 min. captured output:\n$captured")
        }

        assertEquals(0, proc.exitValue(), "runIde exited non-zero. output:\n$captured")

        val kts = Files.walk(output).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.toList()
        }
        assertTrue(kts.isNotEmpty(), "no .kt files produced. output:\n$captured")

        val ideaLog = repoRoot.resolve("runner/build/idea-sandbox/IC-2024.3/log/idea.log")
        if (ideaLog.exists()) {
            val fatal = ideaLog.toFile().useLines { lines ->
                lines.firstOrNull { "[j2k] FATAL" in it }
            }
            assertNull(fatal, "idea.log contains [j2k] FATAL: $fatal")
        }
    }

    private fun locateRepoRoot(): Path {
        // The test runs with cwd = runner/ from gradle, so repo root is the parent.
        // Fall back to user.dir if needed.
        var here = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(3) {
            if (here.resolve("settings.gradle.kts").exists()) return here
            here = here.parent ?: return here
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath()
    }
}
