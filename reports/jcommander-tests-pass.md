# JCommander tests-pass -- demoted to future work

- corpus: 73 .kt files converted from JCommander main/java by the static J2K runner
- kotlinc per-file (isolated): 16/73 (`reports/jcommander-isolated.md`)
- kotlinc batch (module): 0/73 (`reports/jcommander.md`)
- tests-pass: not measured. test sources don't compile against the converted code.

Demoted to future work because the test-compile step blocks before any
test ever runs. The blockers are specific. Naming them so a future fix
has a target:

1. `sun.reflect.annotation.AnnotationParser` -- imported by
   `src/test/java/com/beust/jcommander/parameterized/parser/JsonAnnotationParameterizedParser.java:21`.
   Needs `--add-exports java.base/sun.reflect.annotation=ALL-UNNAMED`
   on the `javac` and `java` lines. JCommander's own gradle test task
   already does this (`build.gradle.kts:92,95`). My
   `scripts/run-jcommander-tests.sh:120` invokes raw `javac` without it,
   so 33 unrelated errors fall out. **Fix**: add `--add-exports
   java.base/sun.reflect.annotation=ALL-UNNAMED` to the `javac` invocation.
2. `Sets.newLinkedHashSet()` etc. -- 18 of the 33 javac errors are
   "non-static method `<K>newLinkedHashSet()` cannot be referenced
   from a static context." The Java side has `public static <K> Set<K>
   newLinkedHashSet()`. J2K dropped it inside a companion-object pattern
   that the .kt-derived `.class` exposes as instance, not static. **Fix**:
   either annotate the relevant utility methods with `@JvmStatic` in the
   converted Kotlin (post-processor pass, similar to `ConstValFix.kt`),
   or layer the original Java `.class` for these utility classes ahead
   of the converted Kotlin on the test classpath, accept the kotlin
   conversion isn't behaviourally drop-in for cross-language callers.
3. Once 1+2 land: only the 16/73 standalone-compiling .kt are usable as
   classpath replacements. The remaining 57 still need to come from the
   original Java `.class`. The script does that already
   (`scripts/run-jcommander-tests.sh:113`, kt-classes ahead of jc-classes
   in classpath order), so a partial tests-pass is reachable -- it's a
   measure of how much behaviour the 16 converted-Kotlin files preserve
   when the rest of the module stays Java.

What this would tell us once it works: per-test-class pass/fail of
JCommander's existing TestNG suite when its 16 standalone-compiling
.kt files replace the corresponding `.class` on the classpath. That's
the closest available "did J2K preserve behaviour" signal short of
full module-compile (which needs the cross-file resolution failures
fixed first).

For now: see `reports/jcommander.md` (module-mode compile rate) and
`reports/jcommander-isolated.md` (per-file diagnostic). The
type-signature mismatches between J2K output and the Java test
contract (nullability + override-modifier propagation) are real and
documented in the per-file errors there; the test-compile step is
gated on the two blockers above before those mismatches even matter.
