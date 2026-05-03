# Proposed fix -- `const val` promotion for public string constants

## What I found

J2K's NJ2K converter does promote `static final` literal fields to
`const val` -- but the heuristic is incomplete. After running the
runner against my edge-case dataset, the constants test produced:

```kotlin
// from edge-cases/02_static_final_constants/Sample.java
object Sample {
    const val RETRY_LIMIT: Int = 3
    const val TIMEOUT_MS: Long = 5000L
    val BASE_PATH: String = "/api/v1"        // not promoted
    const val DEBUG: Boolean = false
    val COMPUTED: Int = 1 + 2                // not promoted (computed)
    val EXCLUDED: IntArray = intArrayOf(1, 2, 3)  // not promoted (array; correct)
}
```

The Java input had every field as `public static final`. Numeric primitives
+ boolean got promoted; the String literal did not.

The pass that handles this is
[`AddConstModifierConversion`](https://github.com/JetBrains/intellij-community/blob/idea/243.21565.193/plugins/kotlin/j2k/shared/src/org/jetbrains/kotlin/nj2k/conversions/AddConstModifierConversion.kt)
in `plugins/kotlin/j2k/shared/src/org/jetbrains/kotlin/nj2k/conversions/AddConstModifierConversion.kt`
(IntelliJ Platform 2024.3 == build `idea/243.21565.193`). The class is 32 lines.

Reading it: the gates are
1. `JKField` with `IMMUTABLE` mutability
2. `JKLiteralExpression` initializer
3. `nullability == NotNull`
4. `fqName in {"kotlin.Boolean","kotlin.Byte","kotlin.Short","kotlin.String","kotlin.Int","kotlin.Float","kotlin.Long","kotlin.Double"}`
5. parent is `JKClass` with `isObjectOrCompanionObject == true`

Gate (4) DOES include `kotlin.String`, so the spec accepts it. The gap is
gate (3): J2K's nullability inferrer doesn't mark a Java `static final
String FOO = "..."` field's Kotlin type as NotNull. The output still reads
`val BASE_PATH: String = "/api/v1"` (declared as non-nullable `String`,
not `String?`), but internally `JKType.nullability` is whatever the
inferrer settled on, which on this fixture is not `NotNull`. So `gate (3)`
short-circuits and the const-modifier never fires.

Re-ran on JCommander to cross-check (73 main/java files, runner output
under `fixtures/jcommander-converted/`). Same shape: every static-final
String comes out as `val FOO: String = "..."`, never `const val`. Numeric
constants do promote when present.

The cross-check against newJ2k's
`staticMembers/PrivateStaticMembers.kt` confirms the
`private const val s = "abc"` form does work for *private* string
constants in JetBrains' own testData -- but that fixture's Java had a
hand-crafted shape the inferrer can resolve. The general public-visibility
case is the one that drops on real corpora.

## The fix

`eval/src/main/kotlin/j2keval/ConstValFix.kt` is a Kotlin post-processor
over J2K output. It does a brace-depth scope walk to find `val` decls
sitting at top level or inside an `object` / `companion object` body,
then promotes them to `const val` when the RHS is a Kotlin
compile-time-constant literal: primitive numeric, boolean, char, or
plain string with no template expressions.

Conservative on purpose. The regex deliberately rejects `val X = 1 + 2`
even though that's `const`-eligible in Kotlin -- promoting expressions
would need either real compile-time evaluation or accepting wrong
promotions. False negatives (promotion misses) get reported in the eval
metrics under "`val` with literal RHS that COULD be `const val`" so a
reader can see what's been left on the table.

## Demo on the actual J2K output

Run the full pipeline:

```
$ bash scripts/run-edge-cases.sh
[runner] converted 15 files to /tmp/j2k-out
[eval] kotlinc pass: 12/15
[fix]  promoted 1 val -> const val in 02_static_final_constants/Sample.kt
[eval] re-run: kotlinc pass: 12/15  (no regressions)
```

Diff on `02_static_final_constants/Sample.kt`:

```diff
- val BASE_PATH: String = "/api/v1"
+ const val BASE_PATH: String = "/api/v1"
```

7 unit tests cover the scope rules
(`eval/src/test/kotlin/j2keval/ConstValFixTest.kt`): top-level OK,
companion OK, object-literal in fun body NO, regular class body NO,
already-const NO double-promote, computed-RHS NO promote.

## Why a post-processor and not a J2K patch

The right fix is upstream in `AddConstModifierConversion` (or in the
nullability inferrer that feeds it). Two paths I'd consider:

1. Drop the `nullability != NotNull` gate when the initializer is a
   non-null literal. A `JKLiteralExpression` whose value is a string,
   numeric, char, or boolean literal cannot be null at runtime; the
   const-modifier conversion can safely add `const` regardless of what
   the inferrer settled on for the declared type.
2. Keep the gate, but special-case the inferrer to set NotNull on a
   field whose initializer is a non-null literal. That changes more
   than just const-promotion (it propagates into other passes that
   read the inferred nullability), so path 1 is smaller.

Either change risks observable binary semantics for downstream Java
callers: a Kotlin `const val X = "..."` field gets inlined at the call
site (its bytecode disappears from the consuming `.class`), so a
recompile of the caller is needed when the constant changes. JetBrains
shipped that behaviour for primitives and bools already, so the
precedent is there for strings.

I shipped this as a post-processor instead because (a) I can't ship a
patch into IntelliJ Platform from outside and (b) the fix-on-output
shape lets you opt in per-project. The regex is a 30-line pass; the
upstream change is similar.

## Things this fix doesn't catch

- `static final` arrays. `private static final int[] FOO = {1,2,3}`
  cannot be `const val` in Kotlin (arrays aren't compile-time constants).
  J2K leaves these as `val` and so do I. The output `EXCLUDED` above
  confirms.
- Computed initializers like `1 + 2`. They are constant in Kotlin's eyes
  but my regex won't promote them; I prefer false negatives to wrong
  promotions.
- Constants whose RHS references another constant (`val Y = X + 1`).
  Could be const if X is const, but I don't track cross-symbol type info.
