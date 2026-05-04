# J2K eval report

- corpus: `fixtures/llm-claude-converted`
- generated: 2026-05-04T19:38:44.098064Z
- files: 15
- kotlinc pass rate: 100.0% (15/15)

## Compile summary

| file | ok | errs | ms |
|------|----|------|----|
| `01_anonymous_runnable.kt` | yes | 0 | 339 |
| `02_static_final_constants.kt` | yes | 0 | 339 |
| `03_nullability_no_annotation.kt` | yes | 0 | 339 |
| `04_varargs_to_array.kt` | yes | 0 | 339 |
| `05_generic_wildcard.kt` | yes | 0 | 339 |
| `06_instanceof_pattern.kt` | yes | 0 | 339 |
| `07_try_with_resources.kt` | yes | 0 | 339 |
| `08_static_utility_class.kt` | yes | 0 | 339 |
| `09_enum_with_body.kt` | yes | 0 | 339 |
| `10_default_interface.kt` | yes | 0 | 339 |
| `11_inner_class_outer_ref.kt` | yes | 0 | 339 |
| `12_overloaded_default_args.kt` | yes | 0 | 339 |
| `13_checked_exception.kt` | yes | 0 | 339 |
| `14_builder_chained.kt` | yes | 0 | 339 |
| `15_array_creation.kt` | yes | 0 | 339 |

## Structural metrics (aggregate)

| metric | total |
|--------|-------|
| LOC (Kotlin) | 203 |
| `!!` not-null asserts | 0 |
| `object :` literal anon classes | 0 |
| `fun interface` declarations | 0 |
| `const val` declarations | 5 |
| `val` declarations (non-const) | 9 |
| `val` with literal RHS that COULD be `const val` | 0 |
| `@Throws(...)` annotations | 2 |
| `inner class` declarations | 2 |
| `vararg` params | 1 |
| `.use {}` resource blocks | 3 |
| `lateinit var` declarations | 0 |

## Structural metrics (PSI -- KotlinCoreEnvironment)

| metric | regex | psi | delta |
|--------|-------|-----|-------|
| !! not-null asserts | 0 | 0 | 0 |
| object expression (anon class) | 0 | 0 | 0 |
| fun interface | 0 | 0 | 0 |
| const val | 5 | 5 | 0 |
| val (non-const) | 9 | 8 | -1 |
| const-eligible val | 0 | 0 | 0 |
| inner class | 2 | 1 | -1 |
| vararg | 1 | 1 | 0 |
| lateinit var | 0 | 0 | 0 |

## Hypothesis checks

Each row tests a single claim about how J2K should handle a Java idiom. Pass rate: 12/12.

| file | tag | passed | expectation | sample |
|------|-----|--------|-------------|--------|
| `01_anonymous_runnable.kt` | sam_lambda_lifted | yes | LLM lifted anon Runnable to SAM lambda (static J2K kept anon-object form) | `private val hook = Runnable {` |
| `01_anonymous_runnable.kt` | not_anon_object_form | yes | LLM did NOT keep the anon-object form | `` |
| `02_static_final_constants.kt` | const_string_promoted | yes | LLM promoted public String static final to const val (J2K skips this) | `const val BASE_PATH` |
| `02_static_final_constants.kt` | const_computed_promoted | yes | LLM promoted compile-time-constant expression (1+2) to const val | `const val COMPUTED` |
| `02_static_final_constants.kt` | array_kept_plain_val | yes | array initializer correctly stays plain val (not const-eligible) | `        val EXCLUDED: IntArray` |
| `06_instanceof_pattern.kt` | smart_cast_inversion | yes | LLM inverted negative instanceof to !is to fit smart-cast scope | `if (o !is Int)` |
| `07_try_with_resources.kt` | use_block | yes | try-with-resources -> .use{} | `.use {` |
| `07_try_with_resources.kt` | not_overconservative_nullable | yes | LLM did NOT mark non-null param as nullable | `` |
| `08_static_utility_class.kt` | top_level_object_for_pure_utility | yes | LLM agrees with J2K on top-level object for pure-utility class | `object Sample {` |
| `08_static_utility_class.kt` | not_companion_for_pure_utility | yes | LLM does NOT add a companion wrapper for the pure-function case | `` |
| `12_overloaded_default_args.kt` | overloads_kept_separate | yes | LLM kept 1-arg overload as a separate fun (no default-arg collapse) | `fun render(s: String): String` |
| `12_overloaded_default_args.kt` | three_overloads_present | yes | full 3-arg render still present | `fun render(s: String, prefix: String, upper: Boolean)` |

## Baseline diff

Compares this corpus against a reference (`--baseline-corpus=...`). Normalized line-level diff. Identical: 0, drifted: 4, baseline missing: 11.

| file | status | deltas |
|------|--------|--------|
| `01_anonymous_runnable.kt` | **drifted** | 2 |
| `02_static_final_constants.kt` | **drifted** | 2 |
| `03_nullability_no_annotation.kt` | **baseline missing** | 0 |
| `04_varargs_to_array.kt` | **baseline missing** | 0 |
| `05_generic_wildcard.kt` | **baseline missing** | 0 |
| `06_instanceof_pattern.kt` | **baseline missing** | 0 |
| `07_try_with_resources.kt` | **drifted** | 4 |
| `08_static_utility_class.kt` | **drifted** | 2 |
| `09_enum_with_body.kt` | **baseline missing** | 0 |
| `10_default_interface.kt` | **baseline missing** | 0 |
| `11_inner_class_outer_ref.kt` | **baseline missing** | 0 |
| `12_overloaded_default_args.kt` | **baseline missing** | 0 |
| `13_checked_exception.kt` | **baseline missing** | 0 |
| `14_builder_chained.kt` | **baseline missing** | 0 |
| `15_array_creation.kt` | **baseline missing** | 0 |

### Drift hunks (first 3)

`01_anonymous_runnable.kt`:

```diff
--- baseline/01_anonymous_runnable.kt
+++ candidate/01_anonymous_runnable.kt
@@ -1,13 +1,7 @@
 package edgecases.anon
 
 class Sample {
-    private val hook: Runnable = object : Runnable {
-        override fun run() {
-            println("hook fired")
-        }
-    }
+    private val hook = Runnable { println("hook fired") }
 
-    fun get(): Runnable? {
-        return hook
-    }
+    fun get(): Runnable = hook
 }
```

`02_static_final_constants.kt`:

```diff
--- baseline/02_static_final_constants.kt
+++ candidate/02_static_final_constants.kt
@@ -1,11 +1,13 @@
 package edgecases.consts
 
-object Sample {
-    const val RETRY_LIMIT: Int = 3
-    const val TIMEOUT_MS: Long = 5000L
-    val BASE_PATH: String = "/api/v1"
-    const val DEBUG: Boolean = false
+class Sample {
+    companion object {
+        const val RETRY_LIMIT: Int = 3
+        const val TIMEOUT_MS: Long = 5000L
+        const val BASE_PATH: String = "/api/v1"
+        const val DEBUG: Boolean = false
 
-    val COMPUTED: Int = 1 + 2
-    val EXCLUDED: IntArray = intArrayOf(1, 2, 3)
+        const val COMPUTED: Int = 1 + 2
+        val EXCLUDED: IntArray = intArrayOf(1, 2, 3)
+    }
 }
```

`07_try_with_resources.kt`:

```diff
--- baseline/07_try_with_resources.kt
+++ candidate/07_try_with_resources.kt
@@ -6,18 +6,14 @@
 
 class Sample {
     @Throws(IOException::class)
-    fun readOne(path: String?): String? {
-        BufferedReader(FileReader(path)).use { r ->
-            return r.readLine()
-        }
-    }
+    fun readOne(path: String): String =
+        BufferedReader(FileReader(path)).use { r -> r.readLine() }
 
     @Throws(IOException::class)
-    fun readTwo(pathA: String?, pathB: String?): String? {
+    fun readTwo(pathA: String, pathB: String): String =
         BufferedReader(FileReader(pathA)).use { a ->
             BufferedReader(FileReader(pathB)).use { b ->
-                return a.readLine() + b.readLine()
+                a.readLine() + b.readLine()
             }
         }
-    }
 }
```

