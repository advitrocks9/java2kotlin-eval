# java2kotlin-eval

Three-piece pipeline scoring Java→Kotlin translation, instrumented for
multi-source comparison.

`eval/` is a standalone Kotlin app that scores any directory of `.kt` files:
- module-wide `kotlinc` compile (single invocation, errors blamed back to
  per-file; `--isolated` per-file mode for fixture corpora that share
  top-level names)
- regex + PSI structural metrics (`!!`, `object :`, `fun interface`,
  `const val`, `vararg`, `inner class`, `.use {}`)
- JavaParser-side metrics on the paired `.java` input → recall ratio
  (did the converter drop any try-with-resources / SAMs / static finals?)
- executable hypothesis checks via `--expectations=<file>` (one regex
  per claim, exits 3 on first violation so CI fails when documented
  behavior drifts)
- normalized line-level baseline diff via `--baseline-corpus=<path>`
  (the candidate-vs-reference signal that complements compile rate
  and structural counts)
- one Kotlin post-processor (`ConstValFix.kt`) that catches the public
  String const-promotion gap I found in static J2K
- structured `.jsonl` artifact per run (`SCHEMA_VERSION = 1`,
  `eval/src/main/kotlin/j2keval/Jsonl.kt`); `j2keval compare a.jsonl
  b.jsonl` joins two runs on `(corpus, source, file)` and emits a
  side-by-side report. The bones of multi-agent benchmarking, not just
  a single-converter scorer.

`runner/` is an IntelliJ plugin (`ApplicationStarter`) that opens an
in-memory project, attaches a JDK + source root, and invokes
`NewJavaToKotlinConverter.elementsToKotlin` from a pooled thread under a
read action. Same architecture Meta describe in their [Kotlinator
post](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/).
What it took to get the converter running outside the IDE, and the
**known platform gap that keeps it from running in CI**, is in
[docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md).

`llm/` is a Claude Sonnet 4.6 translator: same input/output contract as
the runner (Java tree → Kotlin tree) but going through the Anthropic
Messages API instead of the static converter. The capture lives at
`fixtures/llm-claude-converted/` and is scored by the same eval. The
point isn't to declare a winner; it's to show the eval handles two
sources cleanly so it can absorb GPT-5, Gemini, a different prompt,
or a future static J2K version without rewrite.

## Honest scope

CI runs the eval pipeline against committed corpora. The runner plugin
is **not** exercised in CI; the LLM call is **not** invoked in CI.
- `runIde` under xvfb on `ubuntu-latest` hangs in IntelliJ Platform's
  `preloadNonHeadlessServices` before my `ApplicationStarter`
  dispatches; same hang shape on macOS once the sandbox warms up. I
  haven't isolated which non-headless service is the culprit and didn't
  have time to dive deeper into IntelliJ Platform internals.
  `scripts/run-edge-cases.sh` and `scripts/run-jcommander-eval.sh`
  wipe `runner/build/idea-sandbox` before each run as a workaround.
- The LLM call costs money + needs a key. Local-only by design;
  `scripts/run-llm-eval.sh` is the entry point. CI evaluates the
  committed `fixtures/llm-claude-converted/*.kt` captures and never
  hits Anthropic.
- I did not get to runtime-correctness measurement (compile the
  converted Kotlin against the source's original test suite). The
  README in this section already pointed at JCommander as the obvious
  candidate; my time budget didn't reach it. Compile rate +
  structural-recall is necessary but not sufficient -- "did the test
  suite still pass" is the real bar and remains unfilled.

## Headline numbers

`reports/edge-converted.jsonl` (static J2K via runner, locally captured;
4 of 15 cases before the platform hang re-fired):

```
files captured:                   4 / 15
kotlinc pass rate (--module):     4 / 4
hypothesis checks:                8 / 8 passing
const-eligible val (regex):       1   (BASE_PATH = "/api/v1" -- gap)
after ConstValFix.kt:             1   promoted
```

`reports/llm-claude.jsonl` (Claude Sonnet 4.6, all 15 cases captured
locally; CI scores the committed outputs):

```
files captured:                   15 / 15
kotlinc pass rate:                15 / 15
hypothesis checks:                12 / 12 passing
baseline-diff vs static J2K:      4 drifted, 11 baseline-missing
                                  (static J2K only captured 4 of these
                                  before the platform hang re-fired)
const-eligible val:               0   (Claude promoted them upstream;
                                  ConstValFix.kt is dead code on this
                                  corpus)
```

`reports/newj2k.jsonl` (15-pair authentic J2K input/output, fetched
from a pinned commit of `intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`):

```
files:                            15
kotlinc pass rate (--isolated):   14 / 15
hypothesis checks:                11 / 11 passing
java->kotlin recall (SAM iface):  1 / 2 = 0.50    (gap surfaced)
single compile failure: staticMembers/StaticImport.kt -- references p.bar
from a sibling fixture the official tests inject; not a J2K bug
```

The recall metric is the most concrete piece of new evidence: 2 Java
single-abstract-method interfaces in the corpus, only 1 became a
Kotlin `fun interface`. That's the `@FunctionalInterface`-required
gap, surfaced at the corpus aggregate -- previously documented as
prose in `docs/EDGE_CASES.md` and now an executable check.

The headline behavioral diff between static J2K and Sonnet 4.6 (from
`reports/llm-claude.md` baseline-diff section): on
`02_static_final_constants.kt` Claude promoted both `BASE_PATH`
(public String) AND `COMPUTED` (`1 + 2` expression) to `const val`.
Static J2K skips both; my `ConstValFix.kt` post-processor patches the
String case but not the computed-expression case. The LLM beats both
upstream and patched J2K on this fixture. Worth noting because it's
also the smallest possible argument for the role: a static converter
with a hand-written post-processor for every case, vs. an LLM that
gets the case right unprompted.

## Run it

```
brew install gradle openjdk@21
brew install kotlin

./gradlew :eval:test                                # unit tests
./gradlew :llm:test                                 # llm tests (no API call)
bash scripts/fetch-newj2k-fixtures.sh               # pull the 15-pair newJ2k sample

./gradlew :eval:run --args="fixtures/newj2k reports/newj2k.md --isolated \
    --expectations=fixtures/newj2k/expectations.txt --allow-compile-fails=1"

./gradlew :eval:run --args="fixtures/edge-converted reports/edge-raw.md \
    --expectations=fixtures/edge-converted/expectations.txt"

./gradlew :eval:run --args="fixtures/llm-claude-converted reports/llm-claude.md \
    --source=claude-sonnet-4-6 \
    --baseline-corpus=fixtures/edge-converted \
    --expectations=fixtures/llm-claude-converted/expectations.txt"

./gradlew :eval:run --args="compare reports/edge-converted.jsonl \
    reports/llm-claude.jsonl reports/compare.md"
```

To exercise the runner end-to-end (fresh sandbox required, takes 5+
minutes, see `docs/HEADLESS_J2K.md` for the macOS gotcha):

```
J2K_RUN_ACCEPTANCE=1 ./gradlew :runner:test --tests '*Acceptance*'
# or, on a one-shot corpus:
./gradlew :runner:runIde --args="j2k <input-java-dir> <output-kt-dir>"
```

To re-translate via Claude (costs ~$0.15 in API tokens):

```
ANTHROPIC_API_KEY=sk-... bash scripts/run-llm-eval.sh
```

**Privacy note:** that script POSTs the full contents of every `.java`
under `edge-cases/` to `api.anthropic.com`. The committed `edge-cases/`
corpus is hand-written sample code; running on it is fine. Do not point
it at proprietary or licensed source unless your Anthropic data handling
agreement covers that traffic.

## Repo layout

- `eval/` -- scoring app + post-processor + JSONL schema + compare.
- `runner/` -- the static J2K runner plugin (`ApplicationStarter`).
- `llm/` -- Claude Sonnet 4.6 translator. Local-only.
- `edge-cases/` -- 15 hand-written Java cases, each tagged with a
  hypothesis I wrote down before running it through any converter.
  See [`HYPOTHESES.md`](edge-cases/HYPOTHESES.md) for the table and
  [`docs/EDGE_CASES.md`](docs/EDGE_CASES.md) for what landed.
- `fixtures/newj2k/` -- 15-pair sample from pinned intellij-community.
  Cross-check corpus.
- `fixtures/edge-converted/` -- 4 captured `.kt` outputs from a local
  static-J2K runner pass.
- `fixtures/llm-claude-converted/` -- 15 captured `.kt` outputs from
  Claude Sonnet 4.6 over the same edge-cases inputs.
- `docs/CASE_STUDIES.md` -- five before/after pairs annotating
  interesting J2K behaviour.

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

1. Runtime-correctness leg on JCommander: convert, compile the `.kt`
   against the original TestNG suite, count pass rate. Compile rate +
   structural recall are necessary; tests-still-pass is the actual
   bar. The pipeline can score this run with no changes (the eval
   reads any `.kt` directory); JCommander's TestNG harness is the
   piece I didn't have time to wire up.
2. Add a second model leg (GPT-5 via the Codex CLI rotator already on
   my machine, or Gemini). The `--source=<name>` flag and JSONL
   schema are designed for this; the `compare` subcommand already
   joins two sources. The bottleneck is API cost per benchmark run,
   not pipeline shape.
3. Find the specific service behind the
   `preloadNonHeadlessServices` hang. The tail of the cancelled-CI
   `idea.log` (committed under [`docs/headless-j2k-cancel-tail.txt`](docs/headless-j2k-cancel-tail.txt))
   shows `LazyInstanceHolder.initialize` →
   `ApplicationLoader.preloadNonHeadlessServices` as the deadlock
   site. Bisecting which service is the culprit is the obvious next
   move; it would unblock the runner-in-CI path entirely.
