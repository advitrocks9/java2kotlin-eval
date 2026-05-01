# fixtures/llm-claude-converted/

Captured Kotlin output from translating the `edge-cases/` Java corpus via
the Anthropic Messages API (Claude Sonnet 4.6, `llm/` module). Each `.kt`
mirrors a single `edge-cases/<id>/Sample.java`, flattened to
`<id>.kt` so the file layout matches `fixtures/edge-converted/` and
`--baseline-corpus=fixtures/edge-converted` produces a clean side-by-side
diff.

The LLM call is local-only. CI never invokes the API:
- locally: `ANTHROPIC_API_KEY=sk-... bash scripts/run-llm-eval.sh`
- CI: scores whatever .kt files are committed here

When this directory is empty the eval CI job for it is a no-op; the
runner-builds + newj2k cross-check + edge-fixtures-eval jobs cover the
critical path on their own.
