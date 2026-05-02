#!/usr/bin/env bash
# local-only. translate edge-cases/*/Sample.java through claude, then run
# the eval on the captured kotlin. CI doesn't run this -- CI scores the
# committed fixtures under fixtures/llm-claude-converted/.
#
# PRIVACY: every .java under edge-cases/ is POSTed in full to
# api.anthropic.com. fine for the committed sample corpus (hand-written,
# no licensed content). don't run on a target/ clone of jcommander or any
# proprietary tree unless your data handling agreement covers it.
#
# usage:
#   ANTHROPIC_API_KEY=sk-... bash scripts/run-llm-eval.sh
#
# env:
#   J2K_LLM_MODEL=...     default: claude-sonnet-4-5
#   J2K_LLM_OVERWRITE=1   re-translate even if outputs already exist

set -euo pipefail

if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
  echo "ANTHROPIC_API_KEY not set. Export it before running." >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INPUT="$ROOT/edge-cases"
OUTPUT="$ROOT/fixtures/llm-claude-converted"
MODEL="${J2K_LLM_MODEL:-claude-sonnet-4-5}"

echo "[run-llm-eval] input: $INPUT"
echo "[run-llm-eval] output: $OUTPUT"
echo "[run-llm-eval] model: $MODEL"

# each edge-cases/<id>/ has a single Sample.java. flatten into
# llm-claude-converted/<id>.kt so the captured corpus matches the shape
# of fixtures/edge-converted/.
mkdir -p "$OUTPUT"

OVERWRITE_FLAG=""
if [[ "${J2K_LLM_OVERWRITE:-}" == "1" ]]; then OVERWRITE_FLAG="--overwrite"; fi

# stage java inputs flat under a tmp dir so Translator's mirroring spits
# out the flat structure we want.
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

echo "[run-llm-eval] writing provenance manifest"
# emit MANIFEST.json so a reviewer can verify each captured .kt was produced
# from the corresponding .java + the current prompt template. tokens_in /
# tokens_out aren't captured today (the messages api returns usage in the
# response and we don't persist it); add it later if it matters.
python3 - "$ROOT" "$MODEL" "$OUTPUT" <<'PY'
import sys, json, hashlib, datetime, pathlib
root = pathlib.Path(sys.argv[1]); model = sys.argv[2]; out_dir = pathlib.Path(sys.argv[3])
prompt_src = (root / 'llm/src/main/kotlin/j2k/llm/Prompt.kt').read_text()
prompt_sha = hashlib.sha256(prompt_src.encode()).hexdigest()
files = []
for kt in sorted(out_dir.glob('*.kt')):
    case_id = kt.stem
    java = root / 'edge-cases' / case_id / 'Sample.java'
    if not java.exists():
        continue
    files.append({
        'case_id': case_id,
        'input_path': str(java.relative_to(root)),
        'input_sha256': hashlib.sha256(java.read_bytes()).hexdigest(),
        'output_path': str(kt.relative_to(root)),
        'output_sha256': hashlib.sha256(kt.read_bytes()).hexdigest(),
    })
manifest = {
    'model': model,
    'prompt_sha256': prompt_sha,
    'prompt_source': 'llm/src/main/kotlin/j2k/llm/Prompt.kt',
    'generated_at': datetime.datetime.now().astimezone().isoformat(),
    'reproduce': 'ANTHROPIC_API_KEY=... J2K_LLM_OVERWRITE=1 bash scripts/run-llm-eval.sh',
    'files': files,
}
(out_dir / 'MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f'[run-llm-eval] manifest: {len(files)} entries -> {out_dir}/MANIFEST.json')
PY

echo "[run-llm-eval] translation done; running eval"
(cd "$ROOT" && ./gradlew :eval:run --args="fixtures/llm-claude-converted reports/llm-claude.md \
    --source=$MODEL \
    --java-source-root=edge-cases \
    --baseline-corpus=fixtures/edge-converted \
    --expectations=fixtures/llm-claude-converted/expectations.txt")

echo "[run-llm-eval] report at reports/llm-claude.md"
