# java2kotlin-eval

Two-piece pipeline around IntelliJ's static Java→Kotlin converter.

`runner/` is an IntelliJ plugin -- an `ApplicationStarter` that opens an
in-memory project, attaches a JDK + source root, and invokes
`NewJavaToKotlinConverter.elementsToKotlin` from a pooled thread under a
read action. Same architecture Meta describe in their [Kotlinator
post](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/).
What it took to get the converter running outside the IDE, and the
**known platform gap that keeps it from running in CI**, is in
[docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md).

`eval/` is a standalone Kotlin app that scores the converter's output:
- module-wide `kotlinc` compile (single invocation, errors blamed back
  to per-file)
- structural metrics from regex (cheap pass) **and** from PSI walks
  via `kotlin-compiler-embeddable` + `KotlinCoreEnvironment`
- one Kotlin post-processor (`ConstValFix.kt`) that catches the
  `const val` promotion gap I found and reports the metric delta

## Honest scope

CI runs the eval pipeline against committed corpora -- the runner
plugin is **not** exercised in CI. `runIde` under xvfb on
`ubuntu-latest` hangs in IntelliJ Platform's
`preloadNonHeadlessServices` before my `ApplicationStarter`
dispatches; same hang shape on macOS once the sandbox warms up. I have
not isolated which non-headless service is the culprit and didn't have
the time budget to dive deeper into IntelliJ Platform internals.

What that means in practice:
- The four `.kt` files under `fixtures/edge-converted/` are real
  runner output captured locally on a fresh sandbox. Eval runs against
  them in CI.
- `scripts/run-edge-cases.sh` and `scripts/run-jcommander-eval.sh` are
  the local-only paths. `J2K_RUN_ACCEPTANCE=1 ./gradlew :runner:test`
  exercises the runner end-to-end against a one-file corpus.
- The CI workflow has a `runner-builds` job that compiles the plugin +
  runs `verifyPluginProjectConfiguration`. That catches API drift (e.g.
  if a future IntelliJ Platform bump renames `JavaToKotlinAction.Handler`)
  without paying the runIde tax.

## Headline numbers

`reports/newj2k.md` (uploaded as a CI artifact every run, 15-pair
authentic J2K input/output sample fetched from a pinned commit of
`intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`):

```
files: 15
kotlinc pass rate (--isolated):     14/15
hypothesis checks (10 fixtures):    11/11 passing
single failure: staticMembers/StaticImport.kt -- references `p.bar`
from a sibling fixture the official tests inject; not a J2K bug
```

`reports/edge-raw.md` and `reports/edge-fixed.md` (CI artifacts; eval
over the four committed runner outputs, before and after the
`ConstValFix.kt` post-processor):

```
files: 4
kotlinc pass rate (--module):    4/4
hypothesis checks (4 fixtures):  8/8 passing
const-eligible val (regex):      1   <-- BASE_PATH = "/api/v1" in 02_static_final_constants.kt
after ConstValFix:               1 promoted
```

Hypothesis checks come from `fixtures/<corpus>/expectations.txt` -- one
falsifiable line per claim (`relpath | tag | yes|no | regex | desc`). The
eval renders a "Hypothesis checks" table in each report and exits with
code 3 if any expectation fails, so CI fails when J2K behavior we
documented stops being true. See `docs/EDGE_CASES.md` for the claims and
how a known doc drift was caught and corrected by the executable check.

The const-val fix is the headline finding: J2K's NJ2K pass already
promotes `private static final` numeric/boolean primitives but skips
public string literals. `docs/PROPOSED_FIX.md` walks through the gap
with the actual J2K output committed under `fixtures/edge-converted/`.

## Run it

```
brew install gradle openjdk@21
brew install kotlin

./gradlew :eval:test                           # unit tests (post-processor scope rules)
bash scripts/fetch-newj2k-fixtures.sh          # pull the 15-pair newJ2k sample
./gradlew :eval:run --args="fixtures/newj2k reports/newj2k.md --isolated \
    --expectations=fixtures/newj2k/expectations.txt --allow-compile-fails=1"
./gradlew :eval:run --args="fixtures/edge-converted reports/edge-raw.md \
    --expectations=fixtures/edge-converted/expectations.txt"
```

To exercise the runner end-to-end (fresh sandbox required, takes 5+
minutes, see `docs/HEADLESS_J2K.md` for the macOS gotcha):

```
J2K_RUN_ACCEPTANCE=1 ./gradlew :runner:test --tests '*Acceptance*'
# or, on a one-shot corpus:
./gradlew :runner:runIde --args="j2k <input-java-dir> <output-kt-dir>"
```

## Repo layout

- `edge-cases/` -- 15 hand-written Java cases, each tagged with a
  hypothesis I wrote down before running it through the converter. See
  [`HYPOTHESES.md`](edge-cases/HYPOTHESES.md) for the table and
  [`docs/EDGE_CASES.md`](docs/EDGE_CASES.md) for what landed and what
  didn't.
- `fixtures/newj2k/` -- 15-pair sample fetched from a pinned
  intellij-community commit. Cross-check corpus.
- `fixtures/edge-converted/` -- 4 captured `.kt` outputs from a local
  runner pass. Eval CI runs against these.
- `docs/CASE_STUDIES.md` -- five before/after pairs annotating
  interesting J2K behaviour I observed.

## Background reading I leaned on

- Meta, [*Translating Java to Kotlin at
  Scale*](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/)
  -- the architectural source for the ApplicationStarter pattern, plus
  the Kotlinator post-processor pipeline (preprocess → J2K → ~150
  transforms → linters → build-fix) which is way larger in scope than
  this submission.
- JetBrains, [`intellij-community/plugins/kotlin/j2k`](https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/j2k)
  -- the converter source and its `shared/tests/testData/newJ2k`
  baseline.
- The Kotlin discuss thread on [extracting J2K from
  intellij-community](https://discuss.kotlinlang.org/t/extracting-the-new-j2k-transpiler-from-the-intellij-community-project/22169)
  ends with "you can't, J2K is tightly coupled to the IDE." This repo
  is one demonstration that you can, **if** you're willing to ship the
  IDE alongside the plugin -- and an honest record of where that
  premise still cracks.

## What I'd want to do next

1. Find the specific service behind the
   `preloadNonHeadlessServices` hang. The tail of the cancelled-CI
   `idea.log` (committed under [`docs/headless-j2k-cancel-tail.txt`](docs/headless-j2k-cancel-tail.txt))
   shows `LazyInstanceHolder.initialize` →
   `ApplicationLoader.preloadNonHeadlessServices` as the deadlock
   site. Bisecting which service is the culprit is the obvious next
   move; it would unblock the runner-in-CI path entirely.
2. Pair the PSI metrics with the `.java` input via JavaParser. The
   current PSI pass only sees Kotlin output -- "we counted N
   `.use {}` blocks" doesn't answer "did J2K miss any". Real recall
   metric needs Java-side parsing.
3. A runtime-correctness leg on JCommander: convert, compile the
   `.kt` against the original TestNG suite, count pass rate. Compile
   rate is necessary; tests-still-pass is the actual bar.
