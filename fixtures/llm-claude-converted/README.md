# fixtures/llm-claude-converted/

captured kotlin output from running the `edge-cases/` java corpus through
Claude Sonnet 4.6 via the anthropic messages api (`llm/` module). each
`.kt` here corresponds to one `edge-cases/<id>/Sample.java`, flattened to
`<id>.kt` so the layout matches `fixtures/edge-converted/` and the
`--baseline-corpus=fixtures/edge-converted` flag gives a clean side-by-
side diff.

local-only -- CI never calls the api:
- locally: `ANTHROPIC_API_KEY=sk-... bash scripts/run-llm-eval.sh`
- CI: scores whatever .kt files are committed here

if this directory is empty the llm-leg-eval CI job is a no-op. the
other CI jobs (runner-builds + newj2k-cross-check + edge-fixtures-eval)
cover the critical path on their own.
