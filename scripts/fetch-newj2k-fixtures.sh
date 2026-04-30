#!/usr/bin/env bash
# Pulls a curated 15-pair sample of the JetBrains newJ2k testData into
# fixtures/newj2k. Each pair is one Java input + the J2K-produced .kt output
# the IDE regression-tests against. These are authentic J2K outputs - the
# eval harness scores them the same way it scores my own edge-case dataset.
set -euo pipefail

REPO="JetBrains/intellij-community"
# Pinned to keep the fixture corpus reproducible. Bump when new newJ2k cases
# would be useful; don't drift on master.
COMMIT="de434ea74fbec7b4a2183bdd66ae19e96e5db39a"
BASE="plugins/kotlin/j2k/shared/tests/testData/newJ2k"
RAW="https://raw.githubusercontent.com/${REPO}/${COMMIT}/${BASE}"

DEST="$(cd "$(dirname "$0")/.." && pwd)/fixtures/newj2k"
mkdir -p "$DEST"

# (category, basename, my-hypothesis-tag)
PAIRS=(
  "nullability:FieldAssignedWithNull:nullability"
  "nullability:FieldInitializedWithNull:nullability"
  "anonymousClass:localSelfReference:anonymous_class"
  "objectLiteral:AccessThisInsideAnonClass:object_literal"
  "functionalInterfaces:MyRunnable:sam_lambda"
  "functionalInterfaces:NoFunctionalInterfaceAnnotation:sam_lambda"
  "varArg:ellipsisTypeSingleParams:varargs"
  "varArg:varargLengthAccess:varargs"
  "tryWithResource:Simple:try_with_resources"
  "tryWithResource:MultipleResources:try_with_resources"
  "enum:constantsWithBody1:enum_body"
  "staticMembers:StaticImport:static_members"
  "overloads:Override:overload_default"
  "projections:projections:variance"
  "field:nonConstInitializer:field_init"
)

count=0
for entry in "${PAIRS[@]}"; do
  IFS=':' read -r category name tag <<< "$entry"
  out="$DEST/$category/$name"
  mkdir -p "$out"
  # the .java is always present; .kt sometimes has a .k2 variant for the K2 compiler
  # we prefer the plain .kt as the canonical baseline
  for ext in java kt; do
    url="${RAW}/${category}/${name}.${ext}"
    if curl -fsSL "$url" -o "$out/${name}.${ext}"; then
      :
    else
      echo "miss: $url" >&2
      rm -f "$out/${name}.${ext}"
    fi
  done
  echo "$tag,$category,$name" >> "$DEST/index.csv"
  if [ -f "$out/${name}.java" ] && [ -f "$out/${name}.kt" ]; then
    count=$((count + 1))
  fi
done

echo "fetched $count pairs into $DEST"
