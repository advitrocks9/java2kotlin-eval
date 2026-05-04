# J2K eval report

- corpus: `fixtures/newj2k`
- generated: 2026-05-04T19:38:24.525180Z
- files: 15
- kotlinc pass rate: 93.3% (14/15)

## Compile summary

| file | ok | errs | ms |
|------|----|------|----|
| `anonymousClass/localSelfReference/localSelfReference.kt` | yes | 0 | 14544 |
| `enum/constantsWithBody1/constantsWithBody1.kt` | yes | 0 | 13745 |
| `field/nonConstInitializer/nonConstInitializer.kt` | yes | 0 | 4584 |
| `functionalInterfaces/MyRunnable/MyRunnable.kt` | yes | 0 | 5252 |
| `functionalInterfaces/NoFunctionalInterfaceAnnotation/NoFunctionalInterfaceAnnotation.kt` | yes | 0 | 10992 |
| `nullability/FieldAssignedWithNull/FieldAssignedWithNull.kt` | yes | 0 | 7898 |
| `nullability/FieldInitializedWithNull/FieldInitializedWithNull.kt` | yes | 0 | 6985 |
| `objectLiteral/AccessThisInsideAnonClass/AccessThisInsideAnonClass.kt` | yes | 0 | 17713 |
| `overloads/Override/Override.kt` | yes | 0 | 4609 |
| `projections/projections/projections.kt` | yes | 0 | 16330 |
| `staticMembers/StaticImport/StaticImport.kt` | **no** | 2 | 4202 |
| `tryWithResource/MultipleResources/MultipleResources.kt` | yes | 0 | 8623 |
| `tryWithResource/Simple/Simple.kt` | yes | 0 | 19214 |
| `varArg/ellipsisTypeSingleParams/ellipsisTypeSingleParams.kt` | yes | 0 | 4034 |
| `varArg/varargLengthAccess/varargLengthAccess.kt` | yes | 0 | 3933 |

### Compile-error buckets

| bucket | count |
|--------|-------|
| unresolved reference | 2 |

## Structural metrics (aggregate)

| metric | total |
|--------|-------|
| LOC (Kotlin) | 178 |
| `!!` not-null asserts | 0 |
| `object :` literal anon classes | 2 |
| `fun interface` declarations | 1 |
| `const val` declarations | 0 |
| `val` declarations (non-const) | 3 |
| `val` with literal RHS that COULD be `const val` | 0 |
| `@Throws(...)` annotations | 2 |
| `inner class` declarations | 0 |
| `vararg` params | 2 |
| `.use {}` resource blocks | 3 |
| `lateinit var` declarations | 0 |

## Structural metrics (PSI -- KotlinCoreEnvironment)

| metric | regex | psi | delta |
|--------|-------|-----|-------|
| !! not-null asserts | 0 | 0 | 0 |
| object expression (anon class) | 2 | 2 | 0 |
| fun interface | 1 | 1 | 0 |
| const val | 0 | 0 | 0 |
| val (non-const) | 3 | 3 | 0 |
| const-eligible val | 0 | 0 | 0 |
| inner class | 0 | 0 | 0 |
| vararg | 2 | 2 | 0 |
| lateinit var | 0 | 0 | 0 |

## Hypothesis checks

Each row tests a single claim about how J2K should handle a Java idiom. Pass rate: 11/11.

| file | tag | passed | expectation | sample |
|------|-----|--------|-------------|--------|
| `enum/constantsWithBody1/constantsWithBody1.kt` | enum_method_open | yes | enum body override requires open on base method | `open fun bar` |
| `field/nonConstInitializer/nonConstInitializer.kt` | non_const_method_init | yes | non-const initializer stays plain val | `private val field = methodCall()` |
| `field/nonConstInitializer/nonConstInitializer.kt` | function_init_not_const | yes | function-call initializer is NOT const-promoted | `` |
| `functionalInterfaces/MyRunnable/MyRunnable.kt` | fun_interface_promoted | yes | @FunctionalInterface annotated SAM becomes fun interface | `fun interface MyRunnable` |
| `functionalInterfaces/NoFunctionalInterfaceAnnotation/NoFunctionalInterfaceAnnotation.kt` | fun_interface_NOT_promoted_without_annotation | yes | structural SAM without @FunctionalInterface stays plain interface | `` |
| `nullability/FieldAssignedWithNull/FieldAssignedWithNull.kt` | nullable_field_var | yes | field assigned null becomes var: String? | `var s: String?` |
| `nullability/FieldInitializedWithNull/FieldInitializedWithNull.kt` | nullable_init_null | yes | initialized-with-null becomes var: String? = null | `var s: String? = null` |
| `tryWithResource/MultipleResources/MultipleResources.kt` | nested_use_blocks | yes | multi-resource nests two .use {} blocks | `.use { input ->             ByteArrayOutputStream().use {` |
| `tryWithResource/Simple/Simple.kt` | use_block | yes | single-resource -> .use {} | `.use {` |
| `varArg/ellipsisTypeSingleParams/ellipsisTypeSingleParams.kt` | vararg_kept | yes | same on single-param case | `vararg objs` |
| `varArg/varargLengthAccess/varargLengthAccess.kt` | vararg_kept | yes | Java ellipsis -> vararg | `vararg objs` |

## Java -> Kotlin recall

Pairs each `.kt` with its source `.java` (when one exists, 15/15 files here) and counts the same syntactic categories on both sides. Ratio < 1 means J2K dropped occurrences; ratio > 1 means one Java idiom expands into multiple Kotlin ones (e.g. one `try-with-resources` with N resources nests N `.use {}` blocks). The expression-RHS row reports input-side count only, since `const val` on the Kotlin side covers literal AND expression initializers; treat it as a signal of how many candidates the literal-only row is missing.

| category | java | kotlin | ratio |
|----------|------|--------|-------|
| try-with-resources -> .use{} | 3 | 3 | 1.00 |
| anonymous classes -> object literals | 2 | 2 | 1.00 |
| static final w/ literal RHS -> const val | 0 | 0 | n/a |
| static final w/ const-expression RHS (informational) | 0 | - | - |
| varargs -> vararg params | 2 | 2 | 1.00 |
| inner class -> inner class | 0 | 0 | n/a |
| single-abstract-method iface -> fun interface | 2 | 1 | 0.50 |

