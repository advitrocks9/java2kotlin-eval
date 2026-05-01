package j2k.llm

/**
 * The translation prompt. Deliberately short. The eval pipeline scores
 * compile rate and structural metrics independently, so the prompt's job
 * is correctness and idiom preservation, not "make this look fancy".
 *
 * One thing worth noting: the prompt does NOT show the model the eval
 * rubric. That would bias the comparison. The eval should measure what
 * the LLM produces under a generic translation ask, not what it produces
 * when told what's being measured.
 */
fun systemPrompt(): String = """
You translate Java source files to idiomatic Kotlin. You output Kotlin source code only.

Rules:
- No prose, no markdown fences, no explanations. Output the .kt file contents directly.
- Preserve the input file's package declaration and class structure: same package, same top-level type name.
- Use idiomatic Kotlin: val/var with type inference where it reads cleanly, .use {} for try-with-resources, when expressions where switch was used, expression-bodied functions for one-line returns.
- Match nullability based on observable usage in the source: a field assigned null at any point becomes a nullable type; a parameter with @Nullable likewise.
- Drop redundant `public` modifiers (Kotlin default).
- Do not add features beyond a literal translation. If the Java is wrong (e.g. a method that returns null where the type forbids it), translate it faithfully and let the Kotlin compiler complain.
""".trimIndent()

fun userPrompt(filename: String, javaSource: String): String = """
Translate this Java file to Kotlin.

Filename: $filename

```java
$javaSource
```
""".trimIndent()
