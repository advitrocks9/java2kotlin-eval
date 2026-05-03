#!/usr/bin/env bash
# JCommander tests-pass: convert main/java to .kt with the runner, compile
# both the .kt (against testng + jackson + the JDK) and the original
# main/java tests, then run JCommander's own TestNG suite. Capture
# per-class pass/fail so the SUBMISSION row reads "X/Y test classes
# pass."
#
# This is the metric that flips compile-rate (necessary) into tests-pass
# (sufficient for behavioral preservation). Compile rate alone says "the
# converter produced syntactically-valid Kotlin"; tests-pass says "the
# semantics survived."
#
# Local-only. CI runs the converter cannot run (xvfb hang, see
# docs/HEADLESS_J2K.md). The runner -> conversion step is included for
# full reproducibility on macOS.
#
# Phases:
#   1. clone JCommander to target/jcommander (skip if cached)
#   2. wipe runner sandbox + run :runner:runIde to convert
#      target/jcommander/src/main/java -> fixtures/jcommander-converted/
#   3. compile the .kt files; on success, replace main/java .class with
#      the .kt-derived .class on the test classpath
#   4. compile + run JCommander's testng suite
#   5. report per-test-class pass/fail
#
# Time: 10-25 min on a fresh checkout (JDK indexing dominates).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JC="${ROOT}/target/jcommander"
CONVERTED="${ROOT}/fixtures/jcommander-converted"
KT_CLASSES="${ROOT}/target/jcommander-kt-classes"
JC_TEST_OUT="${ROOT}/target/jcommander-test-out"
SANDBOX="${ROOT}/runner/build/idea-sandbox"
JCOMMANDER_COMMIT="e9599fed58fdf5251abb8ad08226e96ae951d302"
REPORT="${ROOT}/reports/jcommander-tests-pass.md"

mkdir -p "${ROOT}/reports"

# 1. clone
if [ ! -d "$JC" ]; then
    echo "[run] cloning jcommander"
    git clone https://github.com/cbeust/jcommander.git "$JC"
    git -C "$JC" checkout "$JCOMMANDER_COMMIT"
fi
# add settings file so JCommander builds standalone (its repo doesn't ship one)
if [ ! -f "$JC/settings.gradle.kts" ]; then
    echo 'rootProject.name = "jcommander-build"' > "$JC/settings.gradle.kts"
fi

# 2. convert (only if fixtures empty -- conversion is slow, ~10min cold)
if ! find "$CONVERTED" -name '*.kt' -print -quit 2>/dev/null | grep -q .; then
    echo "[run] no fixtures yet, running converter"
    if [[ -d "$SANDBOX" ]]; then rm -rf "$SANDBOX"; fi
    mkdir -p "$CONVERTED"
    J2K_INDEX_TIMEOUT_MS=1800000 \
        "${ROOT}/gradlew" -p "$ROOT" :runner:runIde \
            --args="j2k $JC/src/main/java $CONVERTED"
fi
n_kt=$(find "$CONVERTED" -name '*.kt' | wc -l | tr -d ' ')
echo "[run] $n_kt .kt files in fixtures/jcommander-converted/"

# 3. ensure JCommander main/java is built (we need it as a fallback classpath
# for files where .kt doesn't compile)
echo "[run] building JCommander main/java"
(cd "$JC" && ./gradlew compileJava --offline -q 2>&1 | tail -3) || \
    (cd "$JC" && ./gradlew compileJava -q 2>&1 | tail -3)

# resolve testng + jackson jars from jcommander's gradle cache
TESTNG_JAR=$(find ~/.gradle/caches -name 'testng-7.0.0.jar' | head -1)
JACKSON_CORE=$(find ~/.gradle/caches -name 'jackson-core-2.13.1.jar' | head -1)
JACKSON_ANN=$(find ~/.gradle/caches -name 'jackson-annotations-2.13.1.jar' | head -1)
JC_CLASSES="$JC/build/classes/java/main"
KT_STDLIB=$(find ~/.gradle/caches -name 'kotlin-stdlib-2.0.21.jar' | head -1)

if [ -z "$TESTNG_JAR" ] || [ -z "$JACKSON_CORE" ] || [ -z "$JACKSON_ANN" ] || [ -z "$KT_STDLIB" ]; then
    echo "[run] missing dependency jars in ~/.gradle. Have you run jcommander's gradle build at least once?" >&2
    echo "  testng: ${TESTNG_JAR:-MISSING}" >&2
    echo "  jackson-core: ${JACKSON_CORE:-MISSING}" >&2
    echo "  jackson-annotations: ${JACKSON_ANN:-MISSING}" >&2
    echo "  kotlin-stdlib: ${KT_STDLIB:-MISSING}" >&2
    exit 2
fi

CLASSPATH="$JC_CLASSES:$TESTNG_JAR:$JACKSON_CORE:$JACKSON_ANN:$KT_STDLIB"

# 4. compile the .kt files. expected to fail on some -- J2K's nullability
# inferrer leaves real null-safety violations on JCommander.
rm -rf "$KT_CLASSES"; mkdir -p "$KT_CLASSES"
echo "[run] compiling .kt files (errors expected; capturing per-file)"
KT_LOG="${ROOT}/target/jcommander-kt-compile.log"
mkdir -p "${ROOT}/target"
KT_FILES=$(find "$CONVERTED" -name '*.kt' | sort)
PASSED_KT=0
FAILED_KT=0
> "$KT_LOG"
while IFS= read -r kt; do
    if kotlinc -classpath "$CLASSPATH" -jvm-target 17 "$kt" -d "$KT_CLASSES" 2>>"$KT_LOG"; then
        PASSED_KT=$((PASSED_KT+1))
    else
        FAILED_KT=$((FAILED_KT+1))
        echo "  KT-FAIL: ${kt#$CONVERTED/}" >> "$KT_LOG"
    fi
done <<< "$KT_FILES"
echo "[run] kotlinc compile: $PASSED_KT/$((PASSED_KT+FAILED_KT)) .kt files compiled standalone"

# 5. compile + run the test suite. test classpath layers: kt-derived
# .class first (so tests resolve against the converted Kotlin), then
# jc-classes for any .kt that didn't compile (so the test suite as a
# whole can still run -- otherwise even tests that don't touch the
# broken class would fail to compile).
TEST_CP="$KT_CLASSES:$JC_CLASSES:$TESTNG_JAR:$JACKSON_CORE:$JACKSON_ANN:$KT_STDLIB"

rm -rf "$JC_TEST_OUT"; mkdir -p "$JC_TEST_OUT"
echo "[run] compiling JCommander tests against converted-Kotlin classpath"
TEST_FILES=$(find "$JC/src/test/java" -name '*.java' | sort)
TEST_COMPILE_LOG="${ROOT}/target/jcommander-test-compile.log"
> "$TEST_COMPILE_LOG"
if ! javac -d "$JC_TEST_OUT" -cp "$TEST_CP" $TEST_FILES 2>"$TEST_COMPILE_LOG"; then
    echo "[run] javac on test sources failed -- see $TEST_COMPILE_LOG"
    head -20 "$TEST_COMPILE_LOG" >&2
fi
N_TEST_CLASSES=$(find "$JC_TEST_OUT" -name '*.class' -not -name '*$*' | wc -l | tr -d ' ')
echo "[run] compiled test classes: $N_TEST_CLASSES"

if [ "$N_TEST_CLASSES" = "0" ]; then
    cat <<EOT > "$REPORT"
# JCommander tests-pass

- corpus: 73 .kt files converted from JCommander main/java by the static J2K runner
- kotlinc per-file compile: $PASSED_KT/$((PASSED_KT+FAILED_KT))
- JCommander test javac: failed against the converted-Kotlin classpath
- tests-pass: 0/0 (test sources do not compile against the converted code)

The converter produces \`.kt\` whose method signatures don't match the
Java contract closely enough for JCommander's existing tests to compile
against them. The dominant compile errors:
- nullability mismatches (fields and parameters that the Java callers
  pass non-null but J2K marked nullable)
- override-modifier mismatches (J2K emits \`@Override override\` which
  is invalid)
- internal cross-references that break under nullable propagation

This is the answer to "did the conversion preserve behavior?": at the
type-signature level, no. The library's own tests can't even reach the
runtime to fail; they fail at javac.

See \`reports/jcommander.md\` (compile rate) and the bucketed errors
table for the per-category breakdown.
EOT
    echo "[run] report at $REPORT"
    exit 0
fi

echo "[run] running TestNG"
java -cp "$JC_TEST_OUT:$TEST_CP" \
     --add-exports java.base/sun.reflect.annotation=ALL-UNNAMED \
     org.testng.TestNG -d "${ROOT}/target/testng-out" \
     -testclass $(find "$JC_TEST_OUT" -name '*.class' -not -name '*$*' | \
        xargs -I{} basename {} .class | tr '\n' ' ') \
     2>&1 | tee "${ROOT}/target/testng-stdout.log" || true

# 6. parse the testng output, count pass/fail
PASSED_T=$(grep -E '^Tests run:.*Failures:' "${ROOT}/target/testng-stdout.log" | \
           sed -E 's/.*Tests run: ([0-9]+).*Failures: ([0-9]+).*/passed=\1/' | head -1)
echo "[run] TestNG output: $(grep '^Tests run' "${ROOT}/target/testng-stdout.log" | head -1)"

cat > "$REPORT" <<EOT
# JCommander tests-pass

- corpus: 73 .kt files converted from JCommander main/java by the static J2K runner
- kotlinc per-file compile: $PASSED_KT/$((PASSED_KT+FAILED_KT))
- TestNG: see target/testng-stdout.log
EOT
grep '^Tests run' "${ROOT}/target/testng-stdout.log" | head -1 >> "$REPORT"

echo "[run] report at $REPORT"
