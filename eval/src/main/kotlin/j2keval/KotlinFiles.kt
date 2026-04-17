package j2keval

import java.nio.file.Files
import java.nio.file.Path

internal fun collectKotlinFiles(root: Path): List<Path> =
    Files.walk(root).use { stream ->
        stream
            .filter { it.toString().endsWith(".kt") }
            .filter { !it.toString().contains("/.k1.") && !it.toString().contains("/.k2.") }
            .toList()
            .sorted()
    }

internal fun loadExpectations(file: Path): Map<String, Expectation> {
    if (!Files.exists(file)) return emptyMap()
    // small hand-rolled parser: lines look like
    //   path | tag | should-match (yes|no) | regex | description
    return Files.readAllLines(file)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split('|').map { it.trim() }
            if (parts.size < 5) return@mapNotNull null
            parts[0] to Expectation(
                tag = parts[1],
                description = parts[4],
                pattern = parts[3],
                shouldMatch = parts[2] == "yes",
            )
        }
        .toMap()
}
