# Submission -- Agentic Java2Kotlin Eval Pipeline

This file gathers the verbatim text for each portal field the brief asks
for. Repo URL is added at the top once the GitHub remote is created.

---

## Public GitHub repository

(populated at submission time)

---

## How to reproduce locally

The full instructions are in `README.md` at the repo root. Quick version:

```
brew install gradle openjdk@21
brew install kotlin
git clone <this repo>
cd java2kotlin-eval
./gradlew :eval:test
bash scripts/fetch-newj2k-fixtures.sh
./gradlew :eval:run --args="fixtures/newj2k report.md"
```

Tested on macOS 14, Gradle 9.5.0, JDK 21.0.9, kotlinc 2.3.0.

---

## What the pipeline does

Two pieces.

`runner/` is an IntelliJ plugin with an `ApplicationStarter` that drives
`JavaToKotlinAction.Handler.convertFiles` headlessly, the same architecture
Meta describe in their [Kotlinator post](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/).
Loads, opens the input directory as an IntelliJ project, finds Java PSI
files, and calls into J2K. The conversion call currently returns empty
because the auto-opened project has no SDK-configured module -- detail in
[docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md).

`eval/` is a standalone Kotlin app -- the eval logic is in Kotlin, per the
brief's obligatory criterion. For each `.kt` file it: runs `kotlinc` in a
subprocess to check compile, computes structural metrics by regex (`!!`
count, `object :` literal anonymous classes, `fun interface`
declarations, `const val` vs const-eligible `val`, `@Throws`
over-annotation, `inner class`, `vararg`, `.use {}` blocks), and writes a
markdown report. Optional: load an `expectations` file with per-case
hypothesis regex patterns and report pass/fail per hypothesis.

There's also a Kotlin post-processor (`ConstValFix`) that demonstrates one
identified J2K weakness: NJ2K promotes `private static final` literals to
`const val` but stops at plain `val` for the public form. The
post-processor handles the public case opt-in.

---

## Real-world evaluation results

I evaluated against a 15-pair sample of JetBrains' own newJ2k testData --
authentic J2K input/output pairs from
`intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`,
sampled across the categories that map to my edge-case hypotheses
(nullability, anonymousClass, objectLiteral, functionalInterfaces, varArg,
tryWithResource, enum, staticMembers, overloads, projections, field).

```
files: 15
kotlinc pass rate: 93.3% (14/15)
```

The one failure is `staticMembers/StaticImport.kt`: it imports
`p.bar` from a sibling fixture that we don't fetch. Not a J2K bug.

Aggregate structural metrics on the same sample:

```
LOC (Kotlin):                                  178
!! not-null asserts:                             0
object : literal anon classes:                   2
fun interface declarations:                      1
const val declarations:                          0
val (non-const):                                 3
val with literal RHS that COULD be const val:    0
@Throws annotations:                             2
inner class declarations:                        0
vararg params:                                   2
.use {} resource blocks:                         3
```

Findings I drew from the metrics:

1. Zero `!!` in this sample, against my prior expectation that J2K leaks
   `!!` from raw Java types. Either NJ2K is more careful than I thought,
   or my sample doesn't hit the trigger cases.
2. `fun interface` recovery works without the `@FunctionalInterface`
   annotation on the source, suggesting J2K matches the structural shape.
3. `object :` literal anonymous classes still appear in cases where a SAM
   lambda *would* compile -- 2 across this sample. The `localSelfReference`
   case looks like a genuine miss; the `AccessThisInsideAnonClass` is
   correct because the body uses `this@anon`.
4. Try-with-resources -> `.use {}` works for both single and multi-resource
   cases.
5. `private static final String s = "abc"` becomes `private const val s = "abc"`.
   Same shape with `public` does not promote -- documented in the proposed
   fix below.

---

## Edge-case dataset

15 hand-written Java cases under `edge-cases/`, each with a written
hypothesis. Categories: anonymous SAM, static-final-to-const,
nullability without annotations, varargs spread, generic wildcards
(declaration-site variance), Java 17 instanceof binding, try-with-resources
(single and multi), private-ctor utility class, enum with overridden body,
default interface methods, inner-vs-static-nested, overloads to default
arguments, checked exceptions, builder chained, multi-dim array creation.

Full table with hypotheses in [edge-cases/HYPOTHESES.md](edge-cases/HYPOTHESES.md).
Cross-check against JetBrains' newJ2k testData and per-finding writeup in
[docs/EDGE_CASES.md](docs/EDGE_CASES.md).

---

## Proposed fix in Kotlin for one identified failure

`eval/src/main/kotlin/j2keval/ConstValFix.kt` -- a Kotlin post-processor
that promotes `val NAME = LITERAL` declarations sitting at top level or in
a `companion object` to `const val NAME = LITERAL`, when the RHS is a
Kotlin compile-time-constant literal. Demo against
`fixtures/synthetic/Constants.kt`: 5 of 6 `val`s promoted. 7 unit tests
covering the scope rules in `eval/src/test/kotlin/j2keval/ConstValFixTest.kt`.

Full rationale + the binary-compat reason J2K is conservative here in
[docs/PROPOSED_FIX.md](docs/PROPOSED_FIX.md).

---

## Honest gaps

- The runner plugin's `convertFiles` call returns empty in the headless
  config I tested. The plugin loads, opens the project, and reaches the
  J2K entry point, but no .kt files come out. Detailed in
  `docs/HEADLESS_J2K.md` -- the fix is to attach a `JavaSdkImpl` SDK
  before opening the project, modelled on
  `LightProjectDescriptor` from intellij-community's own test setup. I
  ran out of time on the SDK wiring.
- The eval metrics are regex-based, not PSI-based. Setting up
  `KotlinCoreEnvironment` outside a test framework was non-trivial enough
  that I time-boxed it out. Regex is good enough for what I report; PSI
  would be the right path for richer semantic checks.
- 15 fixture pairs is a sample, not a benchmark. With more time I'd run
  against the full ~600 pairs in newJ2k testData and report category-level
  pass rates.

---

## Hypotheses I tested against the converter

- "J2K leaves `object : Runnable` even when the body is a SAM-eligible
  lambda" -- partly true. Recent NJ2K does the lift via
  `FunctionalInterfacesConversion`, but `localSelfReference` shows it
  still misses some cases.
- "J2K promotes `private static final` but not `public static final` to
  `const val`" -- true (private case verified against `PrivateStaticMembers`;
  public case not in their testData but reproducible on synthetic input).
- "J2K leaks `!!` everywhere from raw Java types" -- false on this sample.
  Updated my prior.
- "Try-with-resources to `.use {}` works for the single-resource case but
  not the nested multi-resource case" -- false. Both work.
- "Multi-overload Java methods become idiomatic Kotlin default arguments"
  -- not testable from this sample (`overloads/Override` is about
  override-vs-overload, not the default-argument rewrite). Open question.
