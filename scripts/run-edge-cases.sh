#!/usr/bin/env bash
# end-to-end run on the hand-written edge-case dataset.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/target/edge-out"
REPORT_RAW="${ROOT}/reports/edge-raw.md"
REPORT_FIXED="${ROOT}/reports/edge-fixed.md"
SANDBOX="${ROOT}/runner/build/idea-sandbox"

mkdir -p "${ROOT}/reports"
rm -rf "$OUT"; mkdir -p "$OUT"

# Wipe the IntelliJ sandbox before each runner pass. Per docs/HEADLESS_J2K.md,
# the runner reliably completes on a fresh sandbox but flakes on a warm one
# (preloadNonHeadlessServices coroutine inherits state from the prior run).
# Two seconds of cleanup beats five minutes of staring at a hung process.
if [[ -d "$SANDBOX" ]]; then
  echo "[runner] wiping warm sandbox at $SANDBOX"
  rm -rf "$SANDBOX"
fi

echo "[runner] converting edge-cases/"
"${ROOT}/gradlew" :runner:runIde --args="j2k ${ROOT}/edge-cases ${OUT}"
echo "[runner] produced $(find "$OUT" -name '*.kt' | wc -l | tr -d ' ') .kt files"

echo "[eval] raw J2K output"
"${ROOT}/gradlew" :eval:run --args="$OUT $REPORT_RAW"

echo "[fix] applying const-val post-processor"
"${ROOT}/gradlew" :eval:run --args="fix-const-val $OUT"

echo "[eval] post-fix"
"${ROOT}/gradlew" :eval:run --args="$OUT $REPORT_FIXED"

echo "[done] reports:"
echo "       $REPORT_RAW"
echo "       $REPORT_FIXED"
