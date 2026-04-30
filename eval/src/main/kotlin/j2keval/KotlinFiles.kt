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

internal fun loadExpectations(file: Path): Map<String, List<Expectation>> {
    if (!Files.exists(file)) return emptyMap()
    // small hand-rolled parser: lines look like
    //   path | tag | should-match (yes|no) | regex | description
    // multiple entries per file are allowed -- a single .kt can carry several
    // independent hypotheses (e.g. const-int promotion AND string-const gap on
    // the same constants file).
    //
    // split with limit=5 so trailing `|` characters in the description column
    // are preserved instead of breaking it into more fields. The first four
    // delimiters are real column boundaries; everything from the fourth
    // delimiter onward is the description.
    //
    // Caveat: the regex column (#4) cannot contain a literal `|`. Use
    // character classes (`[ab]`) or escape elsewhere; if a future expectation
    // genuinely needs alternation, switch the separator or add an escape
    // mechanism. None of the committed expectation files hit this today.
    return Files.readAllLines(file)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split('|', limit = 5).map { it.trim() }
            if (parts.size < 5) return@mapNotNull null
            parts[0] to Expectation(
                tag = parts[1],
                description = parts[4],
                pattern = parts[3],
                shouldMatch = parts[2] == "yes",
            )
        }
        .groupBy({ it.first }, { it.second })
}
