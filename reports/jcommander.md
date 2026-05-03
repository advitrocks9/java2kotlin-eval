# J2K eval report

- corpus: `fixtures/jcommander-converted`
- generated: 2026-05-03T13:05:32.523624Z
- files: 73
- kotlinc pass rate: 21.9% (16/73)

## Compile summary

| file | ok | errs | ms |
|------|----|------|----|
| `com/beust/jcommander/DefaultUsageFormatter.kt` | **no** | 90 | 2758 |
| `com/beust/jcommander/DynamicParameter.kt` | **no** | 6 | 1783 |
| `com/beust/jcommander/FuzzyMap.kt` | **no** | 20 | 1847 |
| `com/beust/jcommander/IDefaultProvider.kt` | **no** | 1 | 1687 |
| `com/beust/jcommander/IMainParameter.kt` | yes | 0 | 1842 |
| `com/beust/jcommander/IParameterValidator.kt` | yes | 0 | 1966 |
| `com/beust/jcommander/IParameterValidator2.kt` | yes | 0 | 1942 |
| `com/beust/jcommander/IParameterizedParser.kt` | yes | 0 | 1942 |
| `com/beust/jcommander/IParametersValidator.kt` | yes | 0 | 2094 |
| `com/beust/jcommander/IStringConverter.kt` | yes | 0 | 2098 |
| `com/beust/jcommander/IStringConverterFactory.kt` | yes | 0 | 1953 |
| `com/beust/jcommander/IStringConverterInstanceFactory.kt` | yes | 0 | 1958 |
| `com/beust/jcommander/IUsageFormatter.kt` | yes | 0 | 1857 |
| `com/beust/jcommander/IValueValidator.kt` | yes | 0 | 1910 |
| `com/beust/jcommander/IVariableArity.kt` | yes | 0 | 1866 |
| `com/beust/jcommander/JCommander.kt` | **no** | 375 | 3136 |
| `com/beust/jcommander/MissingCommandException.kt` | yes | 0 | 2101 |
| `com/beust/jcommander/Parameter.kt` | **no** | 13 | 1859 |
| `com/beust/jcommander/ParameterDescription.kt` | **no** | 80 | 2487 |
| `com/beust/jcommander/ParameterException.kt` | **no** | 1 | 1821 |
| `com/beust/jcommander/Parameterized.kt` | **no** | 103 | 2431 |
| `com/beust/jcommander/Parameters.kt` | **no** | 2 | 1772 |
| `com/beust/jcommander/ParametersDelegate.kt` | **no** | 1 | 1692 |
| `com/beust/jcommander/ResourceBundle.kt` | **no** | 3 | 1713 |
| `com/beust/jcommander/StringKey.kt` | **no** | 4 | 1720 |
| `com/beust/jcommander/Strings.kt` | **no** | 4 | 1924 |
| `com/beust/jcommander/SubParameter.kt` | **no** | 1 | 1723 |
| `com/beust/jcommander/UnixStyleUsageFormatter.kt` | **no** | 23 | 2185 |
| `com/beust/jcommander/WrappedParameter.kt` | **no** | 30 | 2053 |
| `com/beust/jcommander/converters/BaseConverter.kt` | yes | 0 | 2037 |
| `com/beust/jcommander/converters/BigDecimalConverter.kt` | **no** | 1 | 1715 |
| `com/beust/jcommander/converters/BooleanConverter.kt` | **no** | 4 | 1725 |
| `com/beust/jcommander/converters/ByteOrderConverter.kt` | **no** | 3 | 1764 |
| `com/beust/jcommander/converters/CharArrayConverter.kt` | **no** | 1 | 1661 |
| `com/beust/jcommander/converters/CharsetConverter.kt` | **no** | 1 | 1672 |
| `com/beust/jcommander/converters/CommaParameterSplitter.kt` | **no** | 3 | 1761 |
| `com/beust/jcommander/converters/DefaultListConverter.kt` | **no** | 4 | 1779 |
| `com/beust/jcommander/converters/DoubleConverter.kt` | **no** | 2 | 1717 |
| `com/beust/jcommander/converters/EnumConverter.kt` | **no** | 6 | 1821 |
| `com/beust/jcommander/converters/FileConverter.kt` | **no** | 1 | 1671 |
| `com/beust/jcommander/converters/FloatConverter.kt` | **no** | 2 | 1702 |
| `com/beust/jcommander/converters/IParameterSplitter.kt` | yes | 0 | 1869 |
| `com/beust/jcommander/converters/ISO8601DateConverter.kt` | **no** | 2 | 1724 |
| `com/beust/jcommander/converters/InetAddressConverter.kt` | **no** | 1 | 1766 |
| `com/beust/jcommander/converters/InstantConverter.kt` | **no** | 4 | 1819 |
| `com/beust/jcommander/converters/IntegerConverter.kt` | **no** | 2 | 1734 |
| `com/beust/jcommander/converters/JavaTimeConverter.kt` | **no** | 3 | 1818 |
| `com/beust/jcommander/converters/LocalDateConverter.kt` | **no** | 3 | 1731 |
| `com/beust/jcommander/converters/LocalDateTimeConverter.kt` | **no** | 3 | 1771 |
| `com/beust/jcommander/converters/LocalTimeConverter.kt` | **no** | 3 | 1721 |
| `com/beust/jcommander/converters/LongConverter.kt` | **no** | 2 | 1706 |
| `com/beust/jcommander/converters/NoConverter.kt` | **no** | 1 | 1664 |
| `com/beust/jcommander/converters/OffsetDateTimeConverter.kt` | **no** | 3 | 1755 |
| `com/beust/jcommander/converters/OffsetTimeConverter.kt` | **no** | 3 | 1730 |
| `com/beust/jcommander/converters/PathConverter.kt` | **no** | 2 | 1875 |
| `com/beust/jcommander/converters/StringConverter.kt` | **no** | 1 | 1570 |
| `com/beust/jcommander/converters/URIConverter.kt` | **no** | 1 | 1737 |
| `com/beust/jcommander/converters/URLConverter.kt` | **no** | 1 | 1746 |
| `com/beust/jcommander/converters/ZonedDateTimeConverter.kt` | **no** | 3 | 1712 |
| `com/beust/jcommander/defaultprovider/EnvironmentVariableDefaultProvider.kt` | **no** | 4 | 1855 |
| `com/beust/jcommander/defaultprovider/PropertyFileDefaultProvider.kt` | **no** | 5 | 2259 |
| `com/beust/jcommander/internal/Console.kt` | yes | 0 | 2393 |
| `com/beust/jcommander/internal/DefaultConsole.kt` | **no** | 6 | 1846 |
| `com/beust/jcommander/internal/DefaultConverterFactory.kt` | **no** | 32 | 2061 |
| `com/beust/jcommander/internal/JDK6Console.kt` | **no** | 9 | 1903 |
| `com/beust/jcommander/internal/Lists.kt` | **no** | 1 | 1723 |
| `com/beust/jcommander/internal/Maps.kt` | **no** | 2 | 1817 |
| `com/beust/jcommander/internal/Nullable.kt` | **no** | 1 | 1694 |
| `com/beust/jcommander/internal/Sets.kt` | yes | 0 | 2067 |
| `com/beust/jcommander/parser/DefaultParameterizedParser.kt` | **no** | 1 | 1643 |
| `com/beust/jcommander/validators/NoValidator.kt` | **no** | 1 | 1640 |
| `com/beust/jcommander/validators/NoValueValidator.kt` | **no** | 1 | 1653 |
| `com/beust/jcommander/validators/PositiveInteger.kt` | **no** | 1 | 1859 |

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

