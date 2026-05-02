package j2k.llm

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Local-only entry point. Translates a directory of .java files to .kt files
 * via the Anthropic API, writes them to outputDir mirroring relative path.
 *
 * PRIVACY NOTICE: every .java file under inputDir is POSTed in full to
 * api.anthropic.com. Do NOT point this at proprietary, confidential, or
 * licensed source unless your Anthropic data handling agreement covers
 * that traffic.
 *
 * Skips files already present in outputDir unless --overwrite is given.
 * That makes "run, run again after fixing one bad output" cheap; the
 * existing successful translations don't pay another API call.
 *
 * Usage:
 *   ANTHROPIC_API_KEY=sk-... ./gradlew :llm:run \
 *     --args="<input-java-dir> <output-kt-dir> [--model=<name>] [--overwrite]"
 *
 * CI never invokes this. CI scores the committed captured outputs in
 * fixtures/llm-claude-converted/.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: j2k-llm <input-java-dir> <output-kt-dir> [--model=<name>] [--overwrite]")
        exitProcess(2)
    }
    val inputDir = Path.of(args[0])
    val outputDir = Path.of(args[1])
    var model = "claude-sonnet-4-6"
    var overwrite = false
    for (a in args.drop(2)) {
        when {
            a.startsWith("--model=") -> model = a.removePrefix("--model=")
            a == "--overwrite" -> overwrite = true
            else -> { System.err.println("unknown flag: $a"); exitProcess(2) }
        }
    }
    if (!inputDir.toFile().isDirectory) {
        System.err.println("[llm] input dir does not exist or is not a directory: $inputDir")
        exitProcess(2)
    }

    println("[llm] translating $inputDir -> $outputDir using $model")
    val client = AnthropicClient(model = model)
    val tx = Translator(complete = client::complete, onFile = { println("[llm] $it") })
    tx.translateCorpus(inputDir, outputDir, overwrite)
    println("[llm] done")
}
