# java2kotlin-eval

three-piece pipeline around the Java->Kotlin problem. eval/ is the scorer,
runner/ runs the static intellij J2K converter outside the IDE, llm/ runs
the same translation task through Claude. one harness, two converters,
same fixtures.

`eval/` is a standalone kotlin app. given a directory of .kt files it
runs `kotlinc` over them (module mode by default, `--isolated` per-file
for fixture corpora that redeclare top-level names), counts structural
metrics by regex and again by PSI, and writes a markdown report plus a
sibling `.jsonl` so a downstream script can join two runs on
`(corpus, source, file)`. extras: hypothesis checks via
`--expectations=<file>` (one regex per claim, exits 3 on a fail so CI
catches drift), a normalized line-level diff against a reference corpus
via `--baseline-corpus=<path>`, javaparser-side counts on the paired
`.java` input for a recall ratio, one post-processor (`ConstValFix.kt`)
that fixes the public-string const-promotion gap i found in static j2k,
and a `j2keval compare` subcommand that side-by-sides two .jsonl runs.

`runner/` is an intellij plugin (an `ApplicationStarter`) that opens an
in-memory project, attaches a JDK + source root, and calls
`NewJavaToKotlinConverter.elementsToKotlin` from a pooled thread under a
read action. same architectural shape Meta describe in their [Kotlinator
post](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/).
the gory details of getting it to run outside the IDE, plus the
**platform gap that keeps it from running in CI**, are in
[docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md).

`llm/` translates the same .java -> .kt contract via the Anthropic
Messages API (Claude Sonnet 4.6 by default). captures land in
`fixtures/llm-claude-converted/` and the same eval scores them. point
isn't to crown a winner -- it's that the eval doesn't care which
converter produced the .kt, so a third or fourth source (gpt-5, gemini,
a different prompt) plugs in via `--source=<name>` without rewriting
anything.

## honest scope

CI runs the eval pipeline against committed corpora. the runner plugin
is **not** exercised in CI; the llm call is **not** invoked in CI either.

- `runIde` under xvfb on ubuntu-latest hangs in IntelliJ Platform's
  `preloadNonHeadlessServices` before my `ApplicationStarter` dispatches.
  same hang shape on macos once the sandbox warms up. i didn't isolate
  which service is the culprit; ran out of time before going deeper into
  platform internals. `scripts/run-edge-cases.sh` and
  `scripts/run-jcommander-eval.sh` wipe `runner/build/idea-sandbox`
  before each run as a workaround.
- the llm call costs money and needs an api key. local-only by design --
  `scripts/run-llm-eval.sh` is the entry point. CI scores the committed
  `fixtures/llm-claude-converted/*.kt` captures and never hits anthropic.
- i didn't get to runtime-correctness measurement (compile the converted
  kotlin against the source's own test suite). compile rate + structural
  recall are necessary but not sufficient -- "do the original tests
  still pass" is the actual bar and it's unfilled. JCommander was the
  obvious candidate (testng harness, single-module, mostly self-
  contained); ran out of clock.

## headline numbers

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

recall metric on newj2k: 2 java SAM interfaces in the corpus, 1 fun
interface in the kotlin output. that's the @FunctionalInterface-required
gap, which i had documented as prose in `docs/EDGE_CASES.md` and is now
an executable check that catches it at the aggregate.

biggest behavioral diff between static j2k and Sonnet 4.6 shows up in
`02_static_final_constants.kt`. Claude promotes both `BASE_PATH` (public
string) and `COMPUTED` (which has `1 + 2` on the rhs) to `const val`.
static j2k skips both, and my `ConstValFix.kt` only patches the string
case. so the LLM beats both upstream and post-processed j2k on this
particular fixture. small data point but worth flagging -- maintaining
a static converter means writing a hand-rolled fix for every case like
this; the llm got the same case right with no special handling.

## run it

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

**privacy note:** the script POSTs the full contents of every .java
under `edge-cases/` to api.anthropic.com. the committed `edge-cases/`
corpus is hand-written sample code so running on it is fine. don't
point it at proprietary or licensed source unless your anthropic data
agreement covers that traffic.

## repo layout

- `eval/` -- scoring app, post-processor, JSONL schema, compare.
- `runner/` -- static J2K runner plugin (the `ApplicationStarter`).
- `llm/` -- Claude Sonnet 4.6 translator. local-only.
- `edge-cases/` -- 15 hand-written java cases, each tagged with a
  hypothesis i wrote down before running it through any converter.
  see [`HYPOTHESES.md`](edge-cases/HYPOTHESES.md) for the table and
  [`docs/EDGE_CASES.md`](docs/EDGE_CASES.md) for what landed.
- `fixtures/newj2k/` -- 15-pair sample fetched from a pinned
  intellij-community commit. cross-check corpus.
- `fixtures/edge-converted/` -- 4 .kt outputs captured locally from
  the static-j2k runner.
- `fixtures/llm-claude-converted/` -- 15 .kt outputs from Claude
  Sonnet 4.6 over the same edge-cases inputs.
- `docs/CASE_STUDIES.md` -- five before/after pairs of j2k behaviour
  i found interesting.

## background reading i leaned on

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

## what i'd want to do next

1. runtime-correctness on jcommander. convert, compile the .kt against
   the original testng suite, count tests-still-pass. compile rate +
   recall are necessary, tests-pass is the actual bar. eval reads any
   .kt dir so this is mostly testng harness wiring, which is what i
   didn't have time for.
2. second model leg -- gpt-5 via the codex cli rotator i already use
   on my machine, or gemini. `--source=<name>` and the JSONL schema
   are already designed for this and `compare` joins two sources, so
   the only real cost is the api spend per benchmark run.
3. bisect which service the `preloadNonHeadlessServices` hang is
   waiting on. the tail of the cancelled-CI `idea.log` (committed at
   [`docs/headless-j2k-cancel-tail.txt`](docs/headless-j2k-cancel-tail.txt))
   shows `LazyInstanceHolder.initialize` ->
   `ApplicationLoader.preloadNonHeadlessServices` as the deadlock
   site. unblocking that frees the runner-in-CI path.
