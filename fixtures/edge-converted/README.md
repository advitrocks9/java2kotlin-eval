# Edge case J2K output -- captured fixtures

These .kt files are the output of running the runner plugin on
`edge-cases/` from the 2026-04-29 22:48 run. Captured before macOS
sandbox state went sideways (see docs/HEADLESS_J2K.md → "Local flake").
On Linux CI the runner reproduces them deterministically.

Each file is the J2K output (after marker-stripping). Useful as a
committed snapshot so the eval module can be exercised end-to-end
without a fresh runner invocation.
