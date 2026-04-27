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
+ boolean got promoted; the String literal did not. Skimming the J2K
postprocessing source, I haven't found the explicit rule that excludes
strings -- the closest pass is `ImplicitOrExplicitTypeConverter` which
deals with declared type vs inferred type. My read is the omission is in
`PromoteToConstConversion` (or similar), which looks at primitives but
not at string-literal RHS. Could be worth filing.

The cross-check against newJ2k's
`staticMembers/PrivateStaticMembers.kt` confirms the
`private const val s = "abc"` form does work for *private* string
constants. So the gap is specifically: public-visibility + string
literal RHS.

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

The cleanest fix is in J2K's NJ2K postprocessing pass
(`PromoteToConstConversion` is where I'd add the string check). That
needs a JetBrains opinion call -- promoting a public string field
changes its observed binary semantics for separately-compiled Java
callers (string interning, inlining at use sites). Since I can't make
that call, I shipped it as an opt-in post-processor that runs *after*
J2K. If the JetBrains answer is "we want this on by default, gated on a
project setting", the regex translates almost 1:1 to a NJ2K
postprocessing pass.

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
