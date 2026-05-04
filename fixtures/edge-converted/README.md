# Edge case J2K output -- captured fixtures

These .kt files are the output of running the runner plugin on the
`edge-cases/` Java sources, captured from one of the runs that
completed end-to-end. Committed as a snapshot so the eval module can
be exercised without re-running the IDE plugin (which is slow).

Each file went through one marker-strip pass to remove J2K's internal
symbol-resolution placeholders (the `/*@@hash@@*/` form) -- those would
normally be cleaned up by the IDE post-processing pass that we bypass.
See `docs/HEADLESS_J2K.md` for why.

Provenance: produced by `runner/` on the matching
`edge-cases/<n>/Sample.java` input. Captured cases: 01, 02, 07, 08
(four of fifteen).

## Why only four

The other eleven (03-06, 09-15) aren't captured because the runner
hangs after the first successful conversion in a single sandbox.
Symptom: after one or two `:runner:runIde` invocations, IntelliJ
Platform's `preloadNonHeadlessServices` re-enters with a warm sandbox
state and stops dispatching to my `ApplicationStarter`.

Diagnostic: `idea.log` tail is committed at
`docs/headless-j2k-cancel-tail.txt`. Top of the cancelled stack:
`LazyInstanceHolder.initialize` ->
`ApplicationLoader.preloadNonHeadlessServices`. Reproduces 100% on
macOS 14.5 once the sandbox warms; fresh-sandbox runs (after `rm -rf
runner/build/idea-sandbox`) succeed about half the time on the first
case, then re-hang on the second.

I did just rerun on a fresh sandbox (case 03) to confirm nothing
silently fixed itself: 2 minutes, BUILD FAILED with exit value 1, no
output. Same failure mode.

## Workarounds I tried

1. wipe `runner/build/idea-sandbox` before each run -- gets one
   conversion through, then fails. This is the workaround that
   captured 01/02/07/08; running 16 cases in series under it ran
   into the warm-state hang at case ~5.
2. `dispatchEvent` polling tweaks via `J2KStarter.kt:117-134` -- these
   fixed the indexing-not-ready problem (`IndexNotReadyException`
   on `KotlinStdlibCacheImpl`), but not the preload hang.
3. `ApplicationStarter.PREMATURE_BLOCK_HANDLER` -- documented in the
   IntelliJ Platform docs but doesn't appear in 2024.3.

What would actually fix it: bisecting which service in
`preloadNonHeadlessServices` is waiting on a Swing event that never
fires under a warm sandbox. That's the unsolved upstream piece.

## What's lost vs the LLM corpus

The eleven missing cases ARE captured by the LLM leg under
`fixtures/llm-claude-converted/`, so the eval still scores Sonnet 4.5
on the full 15. The "static-J2K vs LLM" comparison in
`reports/llm-claude.md` shows "11 baseline-missing" because the
runner only captured 4 of those 15. Re-capturing all 15 statically
would convert that to a full 15-pair side-by-side -- which is item
1 in the README's "next" list.
