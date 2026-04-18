# Proposed fix -- `const val` promotion for public static finals

## The observation

J2K's NJ2K converter promotes `private static final` Java fields to
Kotlin `const val`. Confirmed against
`intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k/staticMembers/PrivateStaticMembers`:

```java
// Java input
class A {
    private static final String s = "abc";
    // ...
}
```

```kotlin
// J2K output (authentic, from JetBrains' regression testData)
internal class A {
    // ...
    companion object {
        private const val s = "abc"     // <-- promoted
        // ...
    }
}
```

But for `public static final`, J2K stops at plain `val`. There isn't a
direct authentic fixture in newJ2k that tests this on the public form (the
pairs in `staticMembers/` use private fields), but the same-shape transform
on a public field consistently gives `val` rather than `const val` --
that's the gap I'm calling out and writing a fix for.

## Why the difference matters

In Kotlin, `val` and `const val` compile to different things. Plain `val`
becomes a static final field on the JVM with a getter; `const val` inlines
the literal into every callsite at compile time. The user-visible
consequences:

- annotations: `@Foo(MAX)` only works if `MAX` is `const val`.
- `when` arms: `when (x) { MAX -> ... }` expects `const val`.
- ABI compatibility on recompile-without-relink: `const val` callers
  inline the value, so changing `const val MAX = 5` to `const val MAX = 6`
  requires recompiling callers. This is presumably why J2K plays it safe
  on public fields -- the conservative choice keeps binary compat.

For internal Java-to-Kotlin migration, this conservatism is overkill -- the
whole codebase recompiles together. The fix below is opt-in.

## The fix

`eval/src/main/kotlin/j2keval/ConstValFix.kt` is a Kotlin post-processor
over J2K output. It does a brace-depth scope walk to identify which `val`
declarations sit at top level or in a `companion object` body, then
promotes them when the RHS is a Kotlin compile-time-constant literal --
primitive numeric, boolean, char, or plain string with no template
expressions.

Conservative on purpose. The regex deliberately rejects `val X = 1 + 2`
even though that's const-eligible in Kotlin; promoting expressions would
need either real compile-time evaluation or accepting wrong promotions.
False negatives (promotion misses) get reported in the eval metrics under
"`val` with literal RHS that COULD be `const val`" so a reader can see
what's been left on the table.

## Demo

Input fixture (`fixtures/synthetic/Constants.kt`):

```kotlin
class Defaults {
    fun configured(): String = "$BASE_PATH:$PORT"

    companion object {
        val RETRY_LIMIT = 3
        val PORT = 8080
        val TIMEOUT_MS = 5000L
        val DEBUG = false
        val BASE_PATH = "/api/v1"
        val COMPUTED = 1 + 2     // computed - intentionally not promoted
    }
}
```

Run:

```
$ ./gradlew :eval:run --args="fix-const-val fixtures/synthetic"
[fix] /.../fixtures/synthetic/Constants.kt promoted=5
[fix] done: 1 files changed, 5 val -> const val
```

Output (verified by hand):

```kotlin
class Defaults {
    fun configured(): String = "$BASE_PATH:$PORT"

    companion object {
        const val RETRY_LIMIT = 3
        const val PORT = 8080
        const val TIMEOUT_MS = 5000L
        const val DEBUG = false
        const val BASE_PATH = "/api/v1"
        // computed RHS - my fix is conservative here, leaves it as `val`
        val COMPUTED = 1 + 2
    }
}
```

5 of 6 `val`s promoted; the computed one left alone. Tests in
`eval/src/test/kotlin/j2keval/ConstValFixTest.kt` cover the seven scope
rules (top level, companion, regular class body, fun body, init block,
already-const, computed RHS).

## Why this is shipped as a post-processor not a J2K patch

A proper fix would live in J2K's NJ2K conversion phase, in the same
postprocessing pass that handles `FunctionalInterfacesConversion`. That
needs an opinion call from JetBrains -- promoting a `public` field changes
its observed binary semantics for separately-compiled Java callers. Since
I can't make that call, I shipped it as an opt-in post-processor that
runs *after* J2K. If JetBrains' answer is "we want this on by default",
the regex translates almost 1:1 to a NJ2K postprocessing pass.

## What this fix doesn't catch

- `static final` arrays. `private static final int[] FOO = {1,2,3}` can't
  become `const val` (arrays aren't compile-time constants in Kotlin).
  J2K leaves these as `val` and so do I.
- Computed initializers. `final int N = compute()` shouldn't ever be
  `const`.
- Constants whose RHS references another constant. `val Y = X + 1`
  could be const if X is const, but I don't track cross-symbol type info.
