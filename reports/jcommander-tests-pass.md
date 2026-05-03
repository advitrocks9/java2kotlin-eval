# JCommander tests-pass

- corpus: 73 .kt files converted from JCommander main/java by the static J2K runner
- kotlinc per-file compile: 16/73
- JCommander test javac: failed against the converted-Kotlin classpath
- tests-pass: 0/0 (test sources do not compile against the converted code)

The converter produces `.kt` whose method signatures don't match the
Java contract closely enough for JCommander's existing tests to compile
against them. The dominant compile errors:
- nullability mismatches (fields and parameters that the Java callers
  pass non-null but J2K marked nullable)
- override-modifier mismatches (J2K emits `@Override override` which
  is invalid)
- internal cross-references that break under nullable propagation

This is the answer to "did the conversion preserve behavior?": at the
type-signature level, no. The library's own tests can't even reach the
runtime to fail; they fail at javac.

See `reports/jcommander.md` (compile rate) and the bucketed errors
table for the per-category breakdown.
