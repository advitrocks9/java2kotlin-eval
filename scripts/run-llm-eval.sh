#!/usr/bin/env bash
# Local-only: translate the edge-cases Java corpus via Claude, then run the
# eval over the captured Kotlin output. CI does not run this -- CI scores
# committed fixtures under fixtures/llm-claude-converted/.
#
# PRIVACY NOTICE: this script POSTs the FULL CONTENTS of every .java file
# under edge-cases/ to api.anthropic.com. Do NOT run it against proprietary,
# confidential, or otherwise-sensitive source code unless your Anthropic
# data handling agreement covers that traffic. The committed `edge-cases/`
# corpus in this repo is hand-written sample code with no licensed
# content; running on it is fine. Running on a JCommander clone or a
# real codebase under target/ would ship that source upstream.
#
# Usage:
#   ANTHROPIC_API_KEY=sk-... bash scripts/run-llm-eval.sh
#
# Env knobs:
#   J2K_LLM_MODEL    overrides the default model (claude-sonnet-4-6)
#   J2K_LLM_OVERWRITE=1   re-translate even if outputs already exist

set -euo pipefail

if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
  echo "ANTHROPIC_API_KEY not set. Export it before running." >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INPUT="$ROOT/edge-cases"
OUTPUT="$ROOT/fixtures/llm-claude-converted"
MODEL="${J2K_LLM_MODEL:-claude-sonnet-4-6}"

echo "[run-llm-eval] input: $INPUT"
echo "[run-llm-eval] output: $OUTPUT"
echo "[run-llm-eval] model: $MODEL"

# Each edge-cases/<id>/ has a single Sample.java. Mirror that into
# llm-claude-converted/<id>.kt so the captured corpus is flat (matches the
# shape of fixtures/edge-converted/).
mkdir -p "$OUTPUT"

OVERWRITE_FLAG=""
if [[ "${J2K_LLM_OVERWRITE:-}" == "1" ]]; then OVERWRITE_FLAG="--overwrite"; fi

# Stage Java inputs flat under a tmp dir so the Translator's mirroring
# produces a flat output structure matching fixtures/edge-converted.
STAGE="$(mktemp -d -t j2k-llm-stage-XXXX)"
trap 'rm -rf "$STAGE"' EXIT
for d in "$INPUT"/*/; do
  case_id=$(basename "$d")
  if [[ -f "$d/Sample.java" ]]; then
    cp "$d/Sample.java" "$STAGE/${case_id}.java"
  fi
done
echo "[run-llm-eval] staged $(ls "$STAGE" | wc -l | tr -d ' ') .java files"

(cd "$ROOT" && ./gradlew :llm:run --args="$STAGE $OUTPUT --model=$MODEL $OVERWRITE_FLAG")

echo "[run-llm-eval] translation done; running eval"
(cd "$ROOT" && ./gradlew :eval:run --args="fixtures/llm-claude-converted reports/llm-claude.md \
    --source=$MODEL \
    --baseline-corpus=fixtures/edge-converted \
    --expectations=fixtures/llm-claude-converted/expectations.txt")

echo "[run-llm-eval] report at reports/llm-claude.md"
