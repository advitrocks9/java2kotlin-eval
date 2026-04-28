# Headless J2K -- the architecture and where I got stuck

The brief asks for "a GitHub Action pipeline that applies the static j2k
converter to the repository." There is no public CLI for J2K. The official
JetBrains line, from [the discuss.kotlinlang.org thread](https://discuss.kotlinlang.org/t/how-to-convert-java-source-files-into-kotlin-in-an-existing-project-using-command-line/1507),
is "Java to Kotlin conversion cannot be implemented correctly outside of
IntelliJ IDEA." Meta got around this by building Kotlinator -- "an IntelliJ
plugin that includes a class extending `ApplicationStarter` and calling
directly into the `JavaToKotlinConverter` class." That is the pattern this
repo uses.

## What works

`runner/src/main/kotlin/j2k/runner/J2KStarter.kt` is the
`ApplicationStarter`. Wiring:

- `runner/build.gradle.kts` pulls IntelliJ IDEA Community 2024.3 via
  `org.jetbrains.intellij.platform` v2.2.1, plus the `org.jetbrains.kotlin`
  bundled plugin (which is where J2K lives) and `com.intellij.java`
  (PSI for Java).
- `META-INF/plugin.xml` registers the starter as `appStarter` with
  `commandName="j2k"`, so the IDE's CLI dispatcher invokes it on
  `--args="j2k <input> <output>"`.
- The starter walks the input directory, opens it as a project via
  `ProjectManager.loadAndOpenProject`, builds a list of `PsiJavaFile`,
  picks the first `Module`, and calls
  `JavaToKotlinAction.Handler.convertFiles(...)`.

Verified by `./gradlew :runner:runIde --args="j2k <input> <output>"`:
- IntelliJ Platform boots in headless mode (~30s).
- The plugin loads (`Plugin to blame: j2k headless runner` in the SEVERE
  log line confirms it's been recognised).
- Project opens (`ProjectManager.loadAndOpenProject` returns non-null).
- File enumeration finds my 15 edge-case Java files.
- The `convertFiles` call doesn't throw.

## Where it gets stuck

`convertFiles` returns empty (zero PSI Kotlin files) and no .kt files end
up on disk. Two contributing causes I've identified:

1. **EDT slow-operation prohibition.** `PsiManager.findFile` on EDT triggers
   a `SlowOperations` SEVERE log because it walks the workspace file index.
   That's logged not raised, but it suggests the conversion path is on the
   wrong thread. The right wiring is to do the file enumeration in a
   `runReadAction { }` off the EDT, then dispatch the conversion back onto
   EDT inside a `WriteCommandAction`. I started but didn't finish the
   threading rework.
2. **No SDK on the auto-opened project.** `ProjectManager.loadAndOpenProject`
   on a directory with no `.idea/` produces an empty project with one
   default module and no JDK. J2K's type resolution silently bails when it
   can't find `java.lang.Object`, etc, and `convertFiles` returns nothing.
   The fix is to attach a `JavaSdkImpl` SDK before opening the project, the
   way `LightProjectDescriptor` does in IntelliJ's own test setup. The
   relevant test fixture I'd model on is in
   `intellij-community/plugins/kotlin/idea/tests/test/org/jetbrains/kotlin/idea/j2k/`.

Both are fixable. The first is cheap -- ~50 lines of threading code. The
second is the real work: it needs care because the SDK has to be
JDK-version-correct or J2K's Java semantic model gives wrong answers.

## Why I shipped without finishing this

Time-boxing. The brief has a deadline. The eval module is the part that's
specified to be in Kotlin, and it's the part where I can show what a useful
J2K eval actually looks like (the metrics, the hypothesis checks, the
const-val post-processor). I treated the runner plugin as the architectural
proof and used JetBrains' own `newJ2k` testData as the working corpus
instead. The .kt files in that testData are what J2K actually produces --
they're the IDE's regression baseline -- so eval numbers against them are
authentic.

If you fix the runner: drop a Java repo into `target/`, run
`./gradlew :runner:runIde --args="j2k target/<repo>/src/main/java target/converted"`,
then `./gradlew :eval:run --args="target/converted report.md"`. The eval
side already works.

## Why not just use the IntelliJ IDE manually?

That's what most teams do. It works but doesn't fit a CI gate -- you can't
run "open IntelliJ, click Convert" on a PR. The whole point of doing the
plugin work is to make J2K runnable as a step in CI alongside lint, type
check, test. That's the one piece of infrastructure JetBrains haven't
shipped publicly yet, even though their February 2026 [VS Code J2K post](https://blog.jetbrains.com/kotlin/2026/02/java-to-kotlin-conversion-comes-to-visual-studio-code/)
suggests it exists internally.
