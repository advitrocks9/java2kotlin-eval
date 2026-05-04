# J2K eval report

- corpus: `fixtures/edge-converted`
- generated: 2026-05-04T19:35:19.186913Z
- files: 4
- kotlinc pass rate: 100.0% (4/4)

## Compile summary

| file | ok | errs | ms |
|------|----|------|----|
| `01_anonymous_runnable.kt` | yes | 0 | 5510 |
| `02_static_final_constants.kt` | yes | 0 | 5510 |
| `07_try_with_resources.kt` | yes | 0 | 5510 |
| `08_static_utility_class.kt` | yes | 0 | 5510 |

## Structural metrics (aggregate)

| metric | total |
|--------|-------|
| LOC (Kotlin) | 62 |
| `!!` not-null asserts | 0 |
| `object :` literal anon classes | 1 |
| `fun interface` declarations | 0 |
| `const val` declarations | 3 |
| `val` declarations (non-const) | 4 |
| `val` with literal RHS that COULD be `const val` | 1 |
| `@Throws(...)` annotations | 2 |
| `inner class` declarations | 0 |
| `vararg` params | 0 |
| `.use {}` resource blocks | 3 |
| `lateinit var` declarations | 0 |

## Structural metrics (PSI -- KotlinCoreEnvironment)

| metric | regex | psi | delta |
|--------|-------|-----|-------|
| !! not-null asserts | 0 | 0 | 0 |
| object expression (anon class) | 1 | 1 | 0 |
| fun interface | 0 | 0 | 0 |
| const val | 3 | 3 | 0 |
| val (non-const) | 4 | 4 | 0 |
| const-eligible val | 1 | 0 | -1 |
| inner class | 0 | 0 | 0 |
| vararg | 0 | 0 | 0 |
| lateinit var | 0 | 0 | 0 |

## Hypothesis checks

Each row tests a single claim about how J2K should handle a Java idiom. Pass rate: 8/8.

| file | tag | passed | expectation | sample |
|------|-----|--------|-------------|--------|
| `01_anonymous_runnable.kt` | anon_object_kept | yes | anon-object form preserved (no SAM lift) for fielded Runnable | `object : Runnable` |
| `02_static_final_constants.kt` | const_int_promoted | yes | numeric static final promotes to const val | `const val RETRY_LIMIT` |
| `02_static_final_constants.kt` | const_long_promoted | yes | long static final promotes to const val | `const val TIMEOUT_MS` |
| `02_static_final_constants.kt` | const_bool_promoted | yes | boolean static final promotes to const val | `const val DEBUG` |
| `02_static_final_constants.kt` | string_const_NOT_promoted | yes | public String static final stays plain val (the gap ConstValFix.kt patches) | `    val BASE_PATH: String = "` |
| `07_try_with_resources.kt` | use_block | yes | try-with-resources -> .use {} (both readOne and readTwo) | `.use {` |
| `08_static_utility_class.kt` | top_level_object | yes | private-ctor utility class becomes top-level object | `object Sample {` |
| `08_static_utility_class.kt` | not_companion_wrapped | yes | members are NOT wrapped in a companion object | `` |

