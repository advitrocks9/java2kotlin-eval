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
and a `j2keval compare` subcommand that emits a per-file compile +
hypothesis cross-tab joining two `.jsonl` runs (not a similarity metric;
just a side-by-side table).

`runner/` is an intellij plugin (an `ApplicationStarter`) that opens an
in-memory project, attaches a JDK + source root, and calls
`NewJavaToKotlinConverter.elementsToKotlin` from a pooled thread under a
read action. same shape as Meta's Kotlinator post (a class extending
`ApplicationStarter` calling directly into the converter -- ref:
[Translating Java to Kotlin at Scale](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/)).
the gory details of getting it to run outside the IDE, plus the
**CI gap** (xvfb-on-ubuntu hangs upstream of my plugin's dispatch),
are in [docs/HEADLESS_J2K.md](docs/HEADLESS_J2K.md).

`llm/` translates the same .java -> .kt contract via the Anthropic
Messages API (Claude Sonnet 4.5 by default). captures land in
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
  on macos the same hang fires once the sandbox warms up. local fix:
  wipe `runner/build/idea-sandbox` before each run. that turned out to
  be enough to convert the full JCommander main/java tree (73 files);
  the CI gap remains.
- second platform issue i did diagnose: J2K's nullability inferrer
  hits `IndexNotReadyException` if it runs before JDK indexing settles.
  `J2KStarter.kt` now polls `DumbService.isDumb` before calling
  `elementsToKotlin`. that change is what unblocked the JCommander run.
- the llm call costs money and needs an api key. local-only by design --
  `scripts/run-llm-eval.sh` is the entry point. CI scores the committed
  `fixtures/llm-claude-converted/*.kt` captures and never hits anthropic.
- runtime-correctness on JCommander: `scripts/run-jcommander-tests.sh`
  attempts to compile the converted kotlin (16/73 standalone), then
  the existing testng suite against it. the test sources fail to
  compile against the converted classpath -- J2K's nullability /
  override-modifier emissions don't match the original Java contract.
  see `reports/jcommander-tests-pass.md` for the breakdown. the
  compile-rate is the real-world finding; tests-pass is 0/0 because the
  test suite can't even javac.

## headline numbers

`reports/jcommander.md` (static J2K via runner, full main/java of cbeust/jcommander
at pinned commit, locally captured):

```
files converted:                  73 / 73
kotlinc pass rate (--isolated):   16 / 73 = 21.9%
                                  (testng + jackson on classpath; per-file)
JCommander tests-pass:            0 / 0
                                  (test sources don't javac against the
                                  converted classpath -- nullability +
                                  override-modifier mismatches)
```

This is the real-world headline. The 21.9% compile rate is the answer
to "does J2K produce code that builds standalone on a real OSS project?"
and the 0/0 tests-pass is the answer to "does it preserve behaviour."
The 21.9% is mostly empty interfaces / enums; substantive classes
(`JCommander.kt`, `ParameterDescription.kt`, `Parameterized.kt`) all
fail. Detail in `reports/jcommander.md` and
`reports/jcommander-tests-pass.md`.

`reports/edge-converted.jsonl` (static J2K via runner on the
hand-written 15-case edge corpus; 4 of 15 captured in the original
2026-04-29 run before the warm-sandbox hang fired, kept for reference):

```
files captured:                   4 / 15
kotlinc pass rate (--isolated):   4 / 4
hypothesis checks:                8 / 8 passing
```

`reports/llm-claude.md` (Claude Sonnet 4.5, all 15 edge cases captured
locally; CI scores the committed outputs):

```
files captured:                   15 / 15
kotlinc pass rate:                15 / 15
hypothesis checks:                12 / 12 passing
baseline-diff vs static J2K:      4 drifted, 11 baseline-missing
                                  (static J2K only captured 4 of these
                                  in the original run)
```

`reports/newj2k.md` (15-pair authentic J2K input/output, fetched from a
pinned commit of `intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`):

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
    --source=claude-sonnet-4-5 \
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
- `llm/` -- Claude Sonnet 4.5 translator. local-only.
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
  the Kotlinator post-processor pipeline (preprocess -> J2K -> ~150
  transforms -> linters -> build-fix) which is way larger in scope than
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
