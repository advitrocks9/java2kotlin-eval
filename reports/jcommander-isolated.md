# J2K eval report

- corpus: `fixtures/jcommander-converted`
- generated: 2026-05-04T19:33:17.863836Z
- files: 73
- kotlinc pass rate: 21.9% (16/73)

## Compile summary

| file | ok | errs | ms |
|------|----|------|----|
| `com/beust/jcommander/DefaultUsageFormatter.kt` | **no** | 90 | 3898 |
| `com/beust/jcommander/DynamicParameter.kt` | **no** | 6 | 2547 |
| `com/beust/jcommander/FuzzyMap.kt` | **no** | 20 | 2642 |
| `com/beust/jcommander/IDefaultProvider.kt` | **no** | 1 | 2432 |
| `com/beust/jcommander/IMainParameter.kt` | yes | 0 | 2827 |
| `com/beust/jcommander/IParameterValidator.kt` | yes | 0 | 3072 |
| `com/beust/jcommander/IParameterValidator2.kt` | yes | 0 | 2924 |
| `com/beust/jcommander/IParameterizedParser.kt` | yes | 0 | 2723 |
| `com/beust/jcommander/IParametersValidator.kt` | yes | 0 | 2887 |
| `com/beust/jcommander/IStringConverter.kt` | yes | 0 | 2793 |
| `com/beust/jcommander/IStringConverterFactory.kt` | yes | 0 | 3148 |
| `com/beust/jcommander/IStringConverterInstanceFactory.kt` | yes | 0 | 3124 |
| `com/beust/jcommander/IUsageFormatter.kt` | yes | 0 | 2894 |
| `com/beust/jcommander/IValueValidator.kt` | yes | 0 | 2863 |
| `com/beust/jcommander/IVariableArity.kt` | yes | 0 | 2779 |
| `com/beust/jcommander/JCommander.kt` | **no** | 375 | 5256 |
| `com/beust/jcommander/MissingCommandException.kt` | yes | 0 | 3162 |
| `com/beust/jcommander/Parameter.kt` | **no** | 13 | 2642 |
| `com/beust/jcommander/ParameterDescription.kt` | **no** | 80 | 3461 |
| `com/beust/jcommander/ParameterException.kt` | **no** | 1 | 2905 |
| `com/beust/jcommander/Parameterized.kt` | **no** | 103 | 3557 |
| `com/beust/jcommander/Parameters.kt` | **no** | 2 | 2564 |
| `com/beust/jcommander/ParametersDelegate.kt` | **no** | 1 | 2578 |
| `com/beust/jcommander/ResourceBundle.kt` | **no** | 3 | 2810 |
| `com/beust/jcommander/StringKey.kt` | **no** | 4 | 2780 |
| `com/beust/jcommander/Strings.kt` | **no** | 4 | 2972 |
| `com/beust/jcommander/SubParameter.kt` | **no** | 1 | 2588 |
| `com/beust/jcommander/UnixStyleUsageFormatter.kt` | **no** | 23 | 3343 |
| `com/beust/jcommander/WrappedParameter.kt` | **no** | 30 | 3480 |
| `com/beust/jcommander/converters/BaseConverter.kt` | yes | 0 | 3140 |
| `com/beust/jcommander/converters/BigDecimalConverter.kt` | **no** | 1 | 2611 |
| `com/beust/jcommander/converters/BooleanConverter.kt` | **no** | 4 | 2597 |
| `com/beust/jcommander/converters/ByteOrderConverter.kt` | **no** | 3 | 2577 |
| `com/beust/jcommander/converters/CharArrayConverter.kt` | **no** | 1 | 2540 |
| `com/beust/jcommander/converters/CharsetConverter.kt` | **no** | 1 | 2477 |
| `com/beust/jcommander/converters/CommaParameterSplitter.kt` | **no** | 3 | 2720 |
| `com/beust/jcommander/converters/DefaultListConverter.kt` | **no** | 4 | 2810 |
| `com/beust/jcommander/converters/DoubleConverter.kt` | **no** | 2 | 2594 |
| `com/beust/jcommander/converters/EnumConverter.kt` | **no** | 6 | 2687 |
| `com/beust/jcommander/converters/FileConverter.kt` | **no** | 1 | 2480 |
| `com/beust/jcommander/converters/FloatConverter.kt` | **no** | 2 | 2544 |
| `com/beust/jcommander/converters/IParameterSplitter.kt` | yes | 0 | 2891 |
| `com/beust/jcommander/converters/ISO8601DateConverter.kt` | **no** | 2 | 3292 |
| `com/beust/jcommander/converters/InetAddressConverter.kt` | **no** | 1 | 2566 |
| `com/beust/jcommander/converters/InstantConverter.kt` | **no** | 4 | 2744 |
| `com/beust/jcommander/converters/IntegerConverter.kt` | **no** | 2 | 2535 |
| `com/beust/jcommander/converters/JavaTimeConverter.kt` | **no** | 3 | 2689 |
| `com/beust/jcommander/converters/LocalDateConverter.kt` | **no** | 3 | 2547 |
| `com/beust/jcommander/converters/LocalDateTimeConverter.kt` | **no** | 3 | 2744 |
| `com/beust/jcommander/converters/LocalTimeConverter.kt` | **no** | 3 | 2953 |
| `com/beust/jcommander/converters/LongConverter.kt` | **no** | 2 | 2613 |
| `com/beust/jcommander/converters/NoConverter.kt` | **no** | 1 | 2616 |
| `com/beust/jcommander/converters/OffsetDateTimeConverter.kt` | **no** | 3 | 2686 |
| `com/beust/jcommander/converters/OffsetTimeConverter.kt` | **no** | 3 | 2845 |
| `com/beust/jcommander/converters/PathConverter.kt` | **no** | 2 | 2980 |
| `com/beust/jcommander/converters/StringConverter.kt` | **no** | 1 | 2357 |
| `com/beust/jcommander/converters/URIConverter.kt` | **no** | 1 | 2675 |
| `com/beust/jcommander/converters/URLConverter.kt` | **no** | 1 | 2580 |
| `com/beust/jcommander/converters/ZonedDateTimeConverter.kt` | **no** | 3 | 2515 |
| `com/beust/jcommander/defaultprovider/EnvironmentVariableDefaultProvider.kt` | **no** | 4 | 2911 |
| `com/beust/jcommander/defaultprovider/PropertyFileDefaultProvider.kt` | **no** | 5 | 3291 |
| `com/beust/jcommander/internal/Console.kt` | yes | 0 | 2988 |
| `com/beust/jcommander/internal/DefaultConsole.kt` | **no** | 6 | 2819 |
| `com/beust/jcommander/internal/DefaultConverterFactory.kt` | **no** | 32 | 3070 |
| `com/beust/jcommander/internal/JDK6Console.kt` | **no** | 9 | 3339 |
| `com/beust/jcommander/internal/Lists.kt` | **no** | 1 | 2690 |
| `com/beust/jcommander/internal/Maps.kt` | **no** | 2 | 2909 |
| `com/beust/jcommander/internal/Nullable.kt` | **no** | 1 | 2953 |
| `com/beust/jcommander/internal/Sets.kt` | yes | 0 | 3144 |
| `com/beust/jcommander/parser/DefaultParameterizedParser.kt` | **no** | 1 | 2932 |
| `com/beust/jcommander/validators/NoValidator.kt` | **no** | 1 | 2530 |
| `com/beust/jcommander/validators/NoValueValidator.kt` | **no** | 1 | 2561 |
| `com/beust/jcommander/validators/PositiveInteger.kt` | **no** | 1 | 2580 |

### Compile-error buckets

| bucket | count |
|--------|-------|
| other | 353 |
| unresolved reference | 271 |
| nullability | 177 |
| type mismatch | 83 |
| abstract / abstract member | 3 |

## Structural metrics (aggregate)

| metric | total |
|--------|-------|
| LOC (Kotlin) | 5582 |
| `!!` not-null asserts | 0 |
| `object :` literal anon classes | 1 |
| `fun interface` declarations | 0 |
| `const val` declarations | 0 |
| `val` declarations (non-const) | 277 |
| `val` with literal RHS that COULD be `const val` | 5 |
| `@Throws(...)` annotations | 11 |
| `inner class` declarations | 2 |
| `vararg` params | 11 |
| `.use {}` resource blocks | 2 |
| `lateinit var` declarations | 0 |

## Structural metrics (PSI -- KotlinCoreEnvironment)

| metric | regex | psi | delta |
|--------|-------|-----|-------|
| !! not-null asserts | 0 | 0 | 0 |
| object expression (anon class) | 1 | 1 | 0 |
| fun interface | 0 | 0 | 0 |
| const val | 0 | 0 | 0 |
| val (non-const) | 277 | 237 | -40 |
| const-eligible val | 5 | 0 | -5 |
| inner class | 2 | 2 | 0 |
| vararg | 11 | 11 | 0 |
| lateinit var | 0 | 0 | 0 |

## Java -> Kotlin recall

Pairs each `.kt` with its source `.java` (when one exists, 73/73 files here) and counts the same syntactic categories on both sides. Ratio < 1 means J2K dropped occurrences; ratio > 1 means one Java idiom expands into multiple Kotlin ones (e.g. one `try-with-resources` with N resources nests N `.use {}` blocks). The expression-RHS row reports input-side count only, since `const val` on the Kotlin side covers literal AND expression initializers; treat it as a signal of how many candidates the literal-only row is missing.

| category | java | kotlin | ratio |
|----------|------|--------|-------|
| try-with-resources -> .use{} | 2 | 2 | 1.00 |
| anonymous classes -> object literals | 1 | 1 | 1.00 |
| static final w/ literal RHS -> const val | 4 | 0 | 0.00 |
| static final w/ const-expression RHS (informational) | 0 | - | - |
| varargs -> vararg params | 9 | 11 | 1.22 |
| inner class -> inner class | 2 | 2 | 1.00 |
| single-abstract-method iface -> fun interface | 13 | 0 | 0.00 |

