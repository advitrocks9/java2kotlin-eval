# java2kotlin-eval

End-to-end pipeline that runs IntelliJ's static Java→Kotlin converter
against a Java source tree, then scores the resulting Kotlin with metrics
written in Kotlin.

Two pieces:

- `runner/` -- IntelliJ plugin. `ApplicationStarter` opens an in-memory
  project, attaches a JDK + source root, and invokes
  `NewJavaToKotlinConverter.elementsToKotlin` from a pooled thread under
  a read action. This is the part Meta call out as hard in their
  [Kotlinator post](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/).
  How I got it working, and what didn't, is in
  [docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md).
- `eval/` -- standalone Kotlin app. Per-file `kotlinc` compile check,
  structural metrics from regex (cheap pass) and from PSI (real pass via
  `kotlin-compiler-embeddable` + `KotlinCoreEnvironment`), markdown
  report. Eval logic is strict-Kotlin per the brief; the runner is
  IntelliJ-platform Kotlin.

## Run it

```
brew install gradle openjdk@21
brew install kotlin

./gradlew :eval:test                          # unit tests
bash scripts/fetch-newj2k-fixtures.sh         # 15-pair sample of authentic J2K
                                              # input/output from intellij-community
bash scripts/run-edge-cases.sh                # convert my own edge cases end-to-end
                                              # (clones IntelliJ on first run, ~3 min)
bash scripts/run-jcommander-eval.sh           # convert cbeust/jcommander
```

The end-to-end runs report to `reports/*.md`. CI in
`.github/workflows/j2k-eval.yml` runs the same pipeline on push.

## What's actually in here

- 15 hand-written Java edge cases under `edge-cases/`, each tagged with
  a hypothesis I wrote down before checking. See
  [edge-cases/HYPOTHESES.md](edge-cases/HYPOTHESES.md) for the table and
  [docs/EDGE_CASES.md](docs/EDGE_CASES.md) for what landed and what
  didn't.
- A 15-pair fixture sample pulled from JetBrains' own
  `intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`,
  used as a cross-check (`scripts/fetch-newj2k-fixtures.sh`).
- The actual JCommander conversion, run through the runner. 73 .java →
  N .kt files, scored in `reports/jcommander-*.md`.
- Five short case studies in [docs/CASE_STUDIES.md](docs/CASE_STUDIES.md)
  picking out interesting things the converter does on the edge cases.
- One Kotlin post-processor (`ConstValFix.kt`) that promotes
  `val NAME = LITERAL` to `const val` for the case J2K misses (public
  string constants). 7 unit tests on the scope rules. Demo in
  [docs/PROPOSED_FIX.md](docs/PROPOSED_FIX.md).

## Headline numbers (newJ2k 15-pair sample)

```
files: 15
kotlinc pass rate: 14/15 (93.3%)

PSI metrics:
  !! not-null asserts:                             0
  object expression (anon class):                  2
  fun interface declarations:                      1
  const val:                                       0
  val (non-const):                                 3
  const-eligible val:                              0
  inner class:                                     0
  vararg params:                                   2
```

Single failure: `staticMembers/StaticImport.kt` references `p.bar` from
a sibling fixture file the official tests inject. Not a J2K bug.

## Background reading I leaned on

- Meta, [*Translating Java to Kotlin at Scale*](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/) -- the architectural source for the ApplicationStarter pattern and for what Kotlinator's post-processor pipeline actually does (preprocess → J2K → ~150 transforms → linters → build-fix).
- JetBrains, [`intellij-community/plugins/kotlin/j2k`](https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/j2k) -- the converter source and its testData.
- The Kotlin discuss thread on
  [extracting J2K from intellij-community](https://discuss.kotlinlang.org/t/extracting-the-new-j2k-transpiler-from-the-intellij-community-project/22169)
  ends with consensus that you can't, J2K is tightly coupled to the IDE.
  This whole repo is one demonstration that you can, *if* you're willing
  to ship the IDE alongside the plugin.

## What I'd want to do next

1. Use the JavaToKotlinAction.Handler post-processing pass instead of
   `elementsToKotlin` directly -- that would give me the IDE's full
   cosmetic + rename-resolution passes. It needs a non-modal progress
   indicator since `withModalProgress` deadlocks in headless. I haven't
   tried.
2. PSI-based eval right now only operates on the .kt output; pairing
   each .kt with its source .java and walking both ASTs in parallel
   would let me say "the Java had 12 try-with-resource blocks, J2K
   produced 12 `.use {}` blocks, none missed." That's a more honest
   recall metric than the count of `.use {}` on the Kotlin side alone.
3. A runtime-correctness leg: convert JCommander, compile the .kt with
   the original Java tests, run the suite, see how many pass. Compile
   rate is necessary; correct-at-runtime is the real bar.
