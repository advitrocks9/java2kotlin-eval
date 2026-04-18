# java2kotlin-eval

Eval pipeline for IntelliJ's static J2K converter. Two parts:

1. `runner/` -- IntelliJ plugin, `ApplicationStarter` that drives
   `JavaToKotlinAction.Handler.convertFiles` headlessly. Same shape Meta
   describe in their [Kotlinator post](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/):
   "an IntelliJ plugin that includes a class extending `ApplicationStarter` and
   calling directly into the `JavaToKotlinConverter` class."
2. `eval/` -- standalone Kotlin app. Per-file `kotlinc` check, structural
   regex metrics, hypothesis check, markdown report. Eval logic is strictly
   Kotlin, per the brief.

The headline number: against a 15-pair sample of JetBrains' own newJ2k
testData, **kotlinc accepts 14/15 (93%)** of the J2K outputs as-is. The one
failure is `staticMembers/StaticImport.kt` referencing a sibling fixture
(`p.Bar`) we don't ship -- not a J2K bug.

## What the pipeline does

```
target Java repo --> [runner plugin / IntelliJ headless] --> .kt files
                                                                   |
                                                                   v
                                              [eval/] kotlinc + metrics + hypothesis
                                                                   |
                                                                   v
                                                              report.md
```

The runner is the part Meta call out as hard. In tested IntelliJ 2024.3
headless, my plugin loads, opens the input dir as a project, finds Java
files via PSI, and calls `convertFiles`. The call returns empty because the
opened project has no SDK-configured module, so type resolution bails.
Documented in [docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md). Eval runs
either way -- it's decoupled from the runner and operates on whatever .kt
input you give it.

## Run it locally

```
brew install gradle openjdk@21      # gradle 9.5+, jdk 21
brew install kotlin                  # kotlinc 2.0+
./gradlew :eval:test                 # 7 unit tests for the const-val fix

# pull a 15-pair sample of authentic J2K output from intellij-community
bash scripts/fetch-newj2k-fixtures.sh

# score it
./gradlew :eval:run --args="fixtures/newj2k report.md"
cat report.md

# apply the const-val post-processor in place
./gradlew :eval:run --args="fix-const-val fixtures/synthetic"
git diff fixtures/synthetic
```

## CI

`.github/workflows/j2k-eval.yml` runs both eval jobs on every push: one
against the newJ2k fixture sample (the working corpus), one against a
freshly cloned JCommander (the Java repo target). The JCommander leg
clones, attempts the headless conversion via the runner plugin, and reports
how many .kt files it produced.

## Headline numbers

| corpus | files | kotlinc passes | structural metrics |
|--------|-------|----------------|--------------------|
| newJ2k 15-pair sample | 15 | 14 (93%) | 0 `!!`, 2 `object :` literal anon, 1 `fun interface`, 3 `.use {}`, 2 `vararg`, 2 `@Throws` |

The 0 `!!` count is the surprise. I expected J2K to leak `!!` from raw Java
types; this sample has zero. The `object :` count is non-zero -- J2K still
falls back to anonymous class expressions in places where a SAM lambda
would do. The const-promotion miss is documented in
[docs/PROPOSED_FIX.md](docs/PROPOSED_FIX.md): private static finals are
promoted but the public ones aren't.

## What I'd do next

1. Fix the runner plugin so `convertFiles` actually produces output. The
   path is to construct a synthetic `JavaSdkImpl` and add it as the project
   SDK before opening the project, the way `LightProjectDescriptor` does in
   IntelliJ's own test setup. I ran out of time on the SDK wiring.
2. Replace the regex metrics with PSI traversal via
   `kotlin-compiler-embeddable` once the runner is producing real output.
   Regex is good enough for the metrics I report but won't generalize.
3. Add a runtime correctness leg: convert JCommander, compile the .kt with
   the original Java tests, run the suite, see how many pass. Compile rate
   is necessary; correct-at-runtime is the bar.
4. Open a discussion on the Kotlin issue tracker about `const`-promotion for
   `public static final` literals. The current behavior is intentional
   (binary compat across recompiles) but worth revisiting.

## Background reading

- Meta, [Translating Java to Kotlin at Scale](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/) -- the architectural source for the ApplicationStarter pattern.
- JetBrains, [intellij-community/plugins/kotlin/j2k](https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/j2k) -- the converter, with `shared/tests/testData/newJ2k` as my fixture corpus.
- Kotlin discuss thread, ["Extracting the new j2k transpiler from intellij-community"](https://discuss.kotlinlang.org/t/extracting-the-new-j2k-transpiler-from-the-intellij-community-project/22169) -- consensus is "you can't, J2K is tightly coupled to the IDE".
