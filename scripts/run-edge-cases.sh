#!/usr/bin/env bash
# end-to-end run on the hand-written edge-case dataset.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/target/edge-out"
REPORT_RAW="${ROOT}/reports/edge-raw.md"
REPORT_FIXED="${ROOT}/reports/edge-fixed.md"

mkdir -p "${ROOT}/reports"
rm -rf "$OUT"; mkdir -p "$OUT"

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
