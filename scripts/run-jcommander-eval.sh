#!/usr/bin/env bash
# End-to-end run on cbeust/jcommander. Convert the full main/java tree,
# eval the result with proper testng + jackson on the kotlinc classpath,
# write a per-file compile report, then apply the const-val post-processor
# and re-eval to show the delta.
#
# For the tests-pass metric (compile JCommander's testng suite against the
# converted .kt and report tests-pass), see scripts/run-jcommander-tests.sh.
#
# CI cannot run this -- the runner hangs under xvfb-on-ubuntu (see
# docs/HEADLESS_J2K.md). Local-only.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${ROOT}/target/jcommander"
CONVERTED="${ROOT}/fixtures/jcommander-converted"
REPORT_RAW="${ROOT}/reports/jcommander.md"
REPORT_FIXED="${ROOT}/reports/jcommander-fixed.md"

mkdir -p "$(dirname "$REPORT_RAW")"

# Pinned so the eval is reproducible. Bump when a newer JCommander would
# materially change what J2K hits (Java 21 features, new annotations, etc).
JCOMMANDER_COMMIT="e9599fed58fdf5251abb8ad08226e96ae951d302"

if [ ! -d "$TARGET" ]; then
    git clone https://github.com/cbeust/jcommander.git "$TARGET"
    git -C "$TARGET" checkout "$JCOMMANDER_COMMIT"
fi
# JCommander's repo doesn't ship a settings.gradle.kts; gradle 8.13+ refuses
# without one when the project sits inside a parent build.
if [ ! -f "$TARGET/settings.gradle.kts" ]; then
    echo 'rootProject.name = "jcommander-build"' > "$TARGET/settings.gradle.kts"
fi

# Build JCommander main/java once (gives us the original .class files +
# downloads testng + jackson into ~/.gradle/caches; we'll need them on the
# kotlinc classpath).
(cd "$TARGET" && ./gradlew compileTestJava -q 2>&1 | tail -3) || true

rm -rf "$CONVERTED"
mkdir -p "$CONVERTED"

SANDBOX="${ROOT}/runner/build/idea-sandbox"
if [[ -d "$SANDBOX" ]]; then
  echo "[run] wiping warm sandbox at $SANDBOX"
  rm -rf "$SANDBOX"
fi

echo "[run] invoking runner plugin"
J2K_INDEX_TIMEOUT_MS=1800000 \
    "${ROOT}/gradlew" :runner:runIde --args="j2k ${TARGET}/src/main/java ${CONVERTED}"

n_kt=$(find "$CONVERTED" -name "*.kt" | wc -l | tr -d ' ')
echo "[run] converted $n_kt .kt files"
if [ "$n_kt" = "0" ]; then
    echo "[run] no kotlin output, see runner/build/idea-sandbox/.../idea.log"
    exit 1
fi

# resolve the testng + jackson + jcommander own classpath for kotlinc
TESTNG=$(find ~/.gradle/caches/modules-2 -name 'testng-7.0.0.jar' | head -1)
JC_CORE=$(find ~/.gradle/caches/modules-2 -name 'jackson-core-2.13.1.jar' | head -1)
JC_ANN=$(find ~/.gradle/caches/modules-2 -name 'jackson-annotations-2.13.1.jar' | head -1)
JC_CL="$TARGET/build/classes/java/main"
export KOTLINC_CLASSPATH="$TESTNG:$JC_CORE:$JC_ANN:$JC_CL"

echo "[run] eval over raw J2K output (--isolated, classpath: testng + jackson + jcommander main)"
"${ROOT}/gradlew" :eval:run --args="$CONVERTED $REPORT_RAW --isolated --java-source-root=$TARGET/src/main/java --allow-compile-fails=99"

echo "[run] applying const-val fix"
"${ROOT}/gradlew" :eval:run --args="fix-const-val $CONVERTED"

echo "[run] re-eval after fix"
"${ROOT}/gradlew" :eval:run --args="$CONVERTED $REPORT_FIXED --isolated --java-source-root=$TARGET/src/main/java --allow-compile-fails=99"

echo "[run] reports: $REPORT_RAW $REPORT_FIXED"
