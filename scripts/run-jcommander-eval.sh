#!/usr/bin/env bash
# End-to-end run on cbeust/jcommander.
#   1. clone jcommander into target/jcommander (if not already)
#   2. invoke the runner plugin to convert src/main/java -> target/converted
#   3. run the eval module against target/converted
#   4. apply the const-val post-processor in place
#   5. re-eval the post-processed tree to show the metric delta
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${ROOT}/target/jcommander"
CONVERTED="${ROOT}/target/converted"
REPORT_RAW="${ROOT}/reports/jcommander-raw.md"
REPORT_FIXED="${ROOT}/reports/jcommander-fixed.md"

mkdir -p "$(dirname "$REPORT_RAW")"

# Pinned so the eval is reproducible. Bump when a newer JCommander would
# materially change what J2K hits (Java 21 features, new annotations, etc).
JCOMMANDER_COMMIT="e9599fed58fdf5251abb8ad08226e96ae951d302"

if [ ! -d "$TARGET" ]; then
    git clone https://github.com/cbeust/jcommander.git "$TARGET"
    git -C "$TARGET" checkout "$JCOMMANDER_COMMIT"
fi

rm -rf "$CONVERTED"
mkdir -p "$CONVERTED"

# Wipe the IntelliJ sandbox before the runner pass. See note in
# scripts/run-edge-cases.sh and docs/HEADLESS_J2K.md -- a warm sandbox
# inherits coroutine state from the prior run and hangs on
# preloadNonHeadlessServices.
SANDBOX="${ROOT}/runner/build/idea-sandbox"
if [[ -d "$SANDBOX" ]]; then
  echo "[run] wiping warm sandbox at $SANDBOX"
  rm -rf "$SANDBOX"
fi

echo "[run] invoking runner plugin"
"${ROOT}/gradlew" :runner:runIde --args="j2k ${TARGET}/src/main/java ${CONVERTED}"

n_kt=$(find "$CONVERTED" -name "*.kt" | wc -l | tr -d ' ')
echo "[run] converted $n_kt .kt files"
if [ "$n_kt" = "0" ]; then
    echo "[run] no kotlin output, see runner/build/idea-sandbox/.../idea.log"
    exit 1
fi

echo "[run] eval over raw J2K output"
"${ROOT}/gradlew" :eval:run --args="$CONVERTED $REPORT_RAW"

echo "[run] applying const-val fix"
"${ROOT}/gradlew" :eval:run --args="fix-const-val $CONVERTED"

echo "[run] re-eval after fix"
"${ROOT}/gradlew" :eval:run --args="$CONVERTED $REPORT_FIXED"

echo "[run] reports: $REPORT_RAW $REPORT_FIXED"
