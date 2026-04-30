# Edge case report

15 hand-written Java cases under `edge-cases/`, plus a 15-pair sample
of authentic J2K input/output from JetBrains' own newJ2k testData.
Each hypothesis was written *before* I looked at how J2K handled it.
This file records what actually happened.

## My hypotheses

| ID | category | hypothesis I wrote down |
|----|----------|-------------------------|
| 01 | SAM lambda | NJ2K's `FunctionalInterfacesConversion` should fire when an anonymous Runnable is captured into a field |
| 02 | const promotion | `static final int X = 7` should become `const val`, not plain `val`, when at the public class level |
| 03 | nullability inference | with no `@Nullable`, J2K guesses; I expected over-conservatism (lots of `String?` for things never null) |
| 04 | varargs spread | `f(arr)` where `arr: String[]` should become `f(*arr)`, not `f(arr)` (which would pass an array as one arg) |
| 05 | declaration-site variance | both `extends` and `super` in one signature -- expected J2K to get one direction wrong |
| 06 | smart cast vs pattern | `if (!(o instanceof T t))` then use `t` after -- Kotlin's smart cast scope is purely lexical, J2K must reshape |
| 07 | try-with-resources | single-resource case clean, multi-resource case probably awkward |
| 08 | utility class | private-ctor + only static methods should become a top-level `object`; J2K would over-conserve to companion |
| 09 | enum w/ body | mixed override + non-override constants can compile in Java but Kotlin needs all-or-none |
| 10 | default interface methods | clean 1:1 mapping expected |
| 11 | inner vs static-nested | implicit outer-this capture -> `inner class` keyword |
| 12 | overloads to defaults | three overloads sharing a default should collapse to one fun w/ default args |
| 13 | checked exceptions | `throws IOException` should drop; over-annotation with `@Throws` is the failure mode |
| 14 | builder chained | self-return type leakage -- `: Builder!` everywhere |
| 15 | array creation | multi-dim like `new int[3][4]` is the stress case |

## Cross-check against newJ2k testData

I picked one fixture per category from
`intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`.
The .kt file in each pair is the IDE's regression baseline -- exactly
what J2K produces today, locked in by JetBrains' own tests.

| my tag | their category | their fixture |
|--------|----------------|---------------|
| nullability | nullability | FieldAssignedWithNull |
| nullability | nullability | FieldInitializedWithNull |
| anonymous_class | anonymousClass | localSelfReference |
| object_literal | objectLiteral | AccessThisInsideAnonClass |
| sam_lambda | functionalInterfaces | MyRunnable |
| sam_lambda | functionalInterfaces | NoFunctionalInterfaceAnnotation |
| varargs | varArg | ellipsisTypeSingleParams |
| varargs | varArg | varargLengthAccess |
| try_with_resources | tryWithResource | Simple |
| try_with_resources | tryWithResource | MultipleResources |
| enum_body | enum | constantsWithBody1 |
| static_members | staticMembers | StaticImport |
| overload_default | overloads | Override |
| variance | projections | projections |
| field_init | field | nonConstInitializer |

## Hypothesis checks

Each claim above has a one-line falsifiable assertion in
`fixtures/<corpus>/expectations.txt`. The eval reads them via
`--expectations=<path>` and emits a "Hypothesis checks" table in the
generated report. A failing check exits the eval with code 3, so CI catches
regressions in J2K behavior we depended on.

```
fixtures/edge-converted/expectations.txt        -- 8 checks across 4 files (static J2K via runner)
fixtures/newj2k/expectations.txt                -- 11 checks across 10 files (JetBrains testData)
fixtures/llm-claude-converted/expectations.txt  -- 12 checks across 6 files (Claude Sonnet 4.6)
```

The Claude expectations are deliberately distinct from the static-J2K
ones: the two converters disagree on several cases (anon-object lift,
companion-vs-top-level for utility classes, const-eligibility of
String + computed-expression literals). Encoding both lets the eval
catch drift on either side independently. The
`reports/llm-claude.md` "Baseline diff" section shows the
side-by-side hunks.

The two corpora carry independent expectation files because their .kt
contents come from different J2K invocations (committed runner output vs.
intellij-community testData baseline at a pinned commit).

## Eval result on this corpus

```
files: 15
kotlinc pass rate: 93.3% (14/15)
LOC (Kotlin): 178

PSI metrics:
  !! not-null asserts:                             0
  object expression (anon class):                  2
  fun interface declarations:                      1
  const val:                                       0
  val (non-const):                                 3
  const-eligible val:                              0
  inner class:                                     0
  vararg params:                                   2
```

Single failure: `staticMembers/StaticImport.kt` references `p.bar`, which
lives in a sibling fixture file the official tests inject. Not a J2K bug.

## Which hypotheses landed and which didn't

**Landed (J2K handles these well):**

- *SAM lambda recovery* (h.01) when annotated. `MyRunnable.kt` becomes
  `fun interface MyRunnable { fun run() }`. The unannotated case
  (`NoFunctionalInterfaceAnnotation`) stays a plain `interface` -- J2K only
  fires the `fun interface` lift when the source carried `@FunctionalInterface`.
  My initial read of the eval output got this backwards; the expectation
  `fun_interface_NOT_promoted_without_annotation` in
  `fixtures/newj2k/expectations.txt` is the executable check that pinned
  the actual behavior.
- *Try-with-resources* (h.07). Single-resource and multi-resource both
  convert to `.use {}`. Multi-resource nests them. Three `.use {}` blocks
  across the two fixtures.
- *Variance projections* (h.05). The `projections.kt` fixture isn't a
  signature-level variance test (it's about generic methods inside a
  body), so my hypothesis isn't really tested by it. Open question.

**Did not land (J2K either over-conservative or wrong):**

- *Const promotion* (h.02). `private static final` does promote
  (verified against `staticMembers/PrivateStaticMembers.kt`: the field
  becomes `private const val`). The public form does NOT promote in any
  fixture I sampled, which lines up with my hypothesis. Documented and
  fixed in [PROPOSED_FIX.md](PROPOSED_FIX.md).
- *Anonymous-class to lambda lift* (h.01, when there's a self-reference).
  `localSelfReference.kt` keeps the `object :` form even though my
  hypothesis predicted lift. J2K's heuristic seems conservative when the
  anon class body refers to itself in any way -- that's defensible
  (lambdas don't have `this@anon`), but `localSelfReference` doesn't
  actually use the self in a way Kotlin couldn't express. Could be a
  smarter check.

**Mixed:**

- *Nullability inference* (h.03). Zero `!!` in the sample. My prior was
  "leaks `!!` everywhere"; updated. NJ2K is more careful than that, at
  least on the cases the regression tests cover.

## What I didn't get to

- Pattern-binding instanceof (h.06). The newJ2k testData has a
  `newJavaFeatures/` directory I didn't sample from; that's where Java 17
  patterns live.
- Builder-style chains (h.14). No good newJ2k case for this; would need
  hand-written input run through the IDE's full convert action.
- Multi-dim array creation (h.15). Same.
- Overloads-to-default-args (h.12). The `Override` fixture is about
  override-vs-overload, not the default-argument rewrite. Would need a
  hand-written case.
