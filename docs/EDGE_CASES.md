# Edge case report

15 hand-written Java cases stress-testing the J2K converter, plus a 15-pair
sample of authentic J2K input/output from JetBrains' own newJ2k testData.

## My edge cases (`edge-cases/`)

Each case lives in its own directory with the Java source and a comment
block stating the hypothesis. Categories are listed in
[edge-cases/HYPOTHESES.md](../edge-cases/HYPOTHESES.md). The runner plugin
is the piece that converts these; while the plugin's `convertFiles` call is
the WIP from [HEADLESS_J2K.md](HEADLESS_J2K.md), the hypotheses below are
testable today by hand against IDEA's Code -> Convert action.

## Cross-check against JetBrains' newJ2k testData

I picked 15 cases from `intellij-community/plugins/kotlin/j2k/shared/tests/testData/newJ2k`
that map onto my hypothesis categories. Each case is a `.java` (input) plus
the `.kt` JetBrains use as the IDE's regression baseline -- i.e., what J2K
actually produces today. Eval results follow.

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

## Eval numbers (15-pair newJ2k sample)

```
files: 15
kotlinc pass rate: 93.3% (14/15)

compile-error buckets:
  unresolved reference: 2

structural metrics (aggregate):
  LOC (Kotlin):                                  178
  !! not-null asserts:                             0
  object : literal anon classes:                   2
  fun interface declarations:                      1
  const val declarations:                          0
  val declarations (non-const):                    3
  val with literal RHS that COULD be const val:    0
  @Throws(...) annotations:                        2
  inner class declarations:                        0
  vararg params:                                   2
  .use {} resource blocks:                         3
```

Run reproduction:

```
bash scripts/fetch-newj2k-fixtures.sh
./gradlew :eval:run --args="fixtures/newj2k report.md"
cat report.md
```

## Findings

**1. Zero `!!` in the J2K output.** I expected J2K to leak `!!` from raw
Java types -- e.g., a Java `Map.get` call should naturally come out as
`map.get(k)!!.length` if J2K assumes the result is non-null at the call
site. Sample of 15 has zero `!!`. Either J2K is more careful than I gave it
credit for, or my sample doesn't hit the trigger cases. I'd want to run on
a 200-file sample to be sure.

**2. `fun interface` recovery works on annotated SAM types.** The
`@FunctionalInterface`-annotated `MyRunnable.java` becomes
`fun interface MyRunnable { fun run() }`. The
`NoFunctionalInterfaceAnnotation.kt` for the same source without the
annotation also becomes `fun interface` -- J2K detects the structural
shape, not just the annotation. Good behavior.

**3. SAM call sites still get `object :` syntax in some places.** The
`AccessThisInsideAnonClass.kt` retains `object :` syntax instead of a
lambda, because the body uses `this@anon` to refer to the anonymous
class itself. That's correct -- a lambda doesn't have a `this`. But
`localSelfReference.kt` does the same thing for an anonymous class with no
self-reference, suggesting J2K doesn't always check whether the lift is
safe. (See `fixtures/newj2k/anonymousClass/localSelfReference/`.)

**4. Try-with-resources to `.use {}` works.** Both single-resource and
multi-resource cases convert cleanly: 3 `.use {}` blocks across 2 files.
The multi-resource case nests `.use` calls. Expected, confirmed.

**5. Const-val promotion: only when the field is `private`.** This is the
case I drill into in [PROPOSED_FIX.md](PROPOSED_FIX.md).

## What I didn't get to

- Variance projections (case 05). My hypothesis was "J2K stumbles on
  signatures mixing `extends` and `super`." The
  `projections/projections.kt` fixture isn't a strong test of this -- it's
  about generic method type inference inside a body, not a signature with
  both bounds. I'd want a hand-written test that's specifically a method
  signature mixing both.
- Pattern-binding instanceof (case 06). The newJ2k testData has a
  `newJavaFeatures/` directory I didn't sample from; that's where Java 17
  patterns live. Future work.
- Builder-style chained returns (case 14). The `kt-13146` case under
  `anonymousClass/` is something else. No good newJ2k case for builders;
  this would need a custom hand-written input + IDE-driven conversion.
