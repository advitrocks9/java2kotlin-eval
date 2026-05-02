package j2k.llm

import java.nio.file.Path
import kotlin.system.exitProcess

// local-only entry point. takes a java input dir, dumps translated kotlin
// to outputDir mirroring relpath. existing files in outputDir are skipped
// unless --overwrite is set so a re-run after one bad output doesn't pay
// the api cost for the rest.
//
// PRIVACY: every .java in inputDir is POSTed in full to api.anthropic.com.
// don't point this at proprietary or licensed source unless your data
// handling agreement covers it.
//
// usage:
//   ANTHROPIC_API_KEY=sk-... ./gradlew :llm:run \
//     --args="<input-java-dir> <output-kt-dir> [--model=<name>] [--overwrite]"
//
// CI never runs this. CI scores the committed captures under
// fixtures/llm-claude-converted/.
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
