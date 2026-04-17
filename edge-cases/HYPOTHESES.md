# Edge case hypotheses

15 hand-picked Java cases targeting parts of Kotlin's type system that Java
can't express, or idioms where one language is concise and the other isn't.
For each: a hypothesis about how J2K will handle it, and what an expert
human would write.

## Categories I'm targeting

| ID | File | Category | What I'm watching |
|----|------|----------|-------------------|
| 01 | `01_anonymous_runnable/Sample.java` | SAM lambda recovery | does NJ2K's `FunctionalInterfacesConversion` fire when the anon class is captured into a field? |
| 02 | `02_static_final_constants/Sample.java` | const promotion | does `static final int X = 7` become `const val` or just `val`? |
| 03 | `03_nullability_no_annotation/Sample.java` | nullability inference | over-conservative on field set in ctor; does it see the throw-on-null pattern? |
| 04 | `04_varargs_to_array/Sample.java` | varargs spread | call-site array -> spread (`*arr`) or fall-back? |
| 05 | `05_generic_wildcard/Sample.java` | declaration-site variance | both `extends` and `super` in one signature |
| 06 | `06_instanceof_pattern/Sample.java` | smart cast vs pattern binding | `!(o instanceof T t)` flow narrowing |
| 07 | `07_try_with_resources/Sample.java` | `.use {}` rewrite | single vs two-resource case |
| 08 | `08_static_utility_class/Sample.java` | `object` vs `companion object` | private-ctor utility class detection |
| 09 | `09_enum_with_body/Sample.java` | enum override + default | mix of overriding and non-overriding constants |
| 10 | `10_default_interface/Sample.java` | interface default method | clean 1:1 mapping |
| 11 | `11_inner_class_outer_ref/Sample.java` | `inner` vs static-nested | implicit outer capture |
| 12 | `12_overloaded_default_args/Sample.java` | default arguments | three overloads collapsed to one? |
| 13 | `13_checked_exception/Sample.java` | `throws` vs `@Throws` | does it over-annotate? |
| 14 | `14_builder_chained/Sample.java` | platform types in chain | self-return type leakage |
| 15 | `15_array_creation/Sample.java` | array constructors | multi-dim handling |

## Why these and not others

I cut: enum with constructor args (J2K solid), records (Kotlin data classes are 1:1, J2K nails it), labelled break (1:1), assert (drops cleanly). Boring once you know the answer.

What I kept is concentrated in places where Java has one shape and idiomatic Kotlin has a different shape, OR where the type systems disagree (variance, nullability, smart-cast scope). Those are the cases where a 1:1 syntactic translator falls down.

## What an evaluation should actually score

Compilation rate is necessary but the easy bar. The harder question is whether the output is *what a human would write*. For each case I capture both:

1. Does it compile under `kotlinc -script` against the same JDK?
2. Does the output match the idiomatic shape (regex patterns specific to each case, e.g. `const val` for case 02, `inner class` for case 11, `Runnable \{` for case 01)?

Cases where #1 passes but #2 fails are the interesting ones. Those go in the report.
