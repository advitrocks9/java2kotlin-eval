# Edge case J2K output -- captured fixtures

These .kt files are the output of running the runner plugin on the
`edge-cases/` Java sources, captured from one of the runs that
completed end-to-end. Committed as a snapshot so the eval module can
be exercised without re-running the IDE plugin (which is slow).

Each file went through one marker-strip pass to remove J2K's internal
symbol-resolution placeholders (the `/*@@hash@@*/` form) -- those would
normally be cleaned up by the IDE post-processing pass that we bypass.
See `docs/HEADLESS_J2K.md` for why.

Provenance for each fixture: produced by `runner/` on the matching
`edge-cases/<n>/Sample.java` input. The full edge-case corpus is 15
cases; this snapshot captures four of them. The rest reproduce the
same way when the runner runs cleanly.
