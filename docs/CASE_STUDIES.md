# Case studies -- five files J2K got interesting

Five cases where the converter's output told me something I didn't know
going in. Cases 1, 2, 3, 5 come from running the `runner/` plugin on the
`edge-cases/` corpus (committed under `fixtures/edge-converted/`). Case
4 is from `intellij-community`'s own newJ2k testData -- I include it
because the SAM-recovery behaviour it shows isn't covered by my
edge-case dataset.

To reproduce: `bash scripts/run-edge-cases.sh`. The eval report writes
to `reports/edge-raw.md` and `reports/edge-fixed.md`.

## 1. The static utility class becomes `object`, not `class + companion`

**Java input** (`edge-cases/08_static_utility_class/Sample.java`):

```java
public final class Sample {
    private Sample() {}
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(v, hi)); }
    public static int sign(int v) { return Integer.compare(v, 0); }
}
```

**J2K output** (after marker strip):

```kotlin
object Sample {
    fun clamp(v: Int, lo: Int, hi: Int): Int = Math.max(lo, Math.min(v, hi))
    fun sign(v: Int): Int = Integer.compare(v, 0)
}
```

I had this down as a hypothesis-failure case -- I expected `class +
companion`. J2K does the right thing, lifts to top-level `object`, drops
the private constructor. The expression-body conversion (`fun ... = ...`)
is also nice. This is the case where my "I expected over-conservatism"
prediction was wrong.

## 2. Static-final constants -- primitives promote, strings don't

**Java input** (`edge-cases/02_static_final_constants/Sample.java`):

```java
public class Sample {
    public static final int RETRY_LIMIT = 3;
    public static final long TIMEOUT_MS = 5000L;
    public static final String BASE_PATH = "/api/v1";
    public static final boolean DEBUG = false;
    public static final int COMPUTED = 1 + 2;
    public static final int[] EXCLUDED = {1, 2, 3};
}
```

**J2K output**:

```kotlin
object Sample {
    const val RETRY_LIMIT: Int = 3
    const val TIMEOUT_MS: Long = 5000L
    val BASE_PATH: String = "/api/v1"          // <-- not promoted
    const val DEBUG: Boolean = false
    val COMPUTED: Int = 1 + 2                  // <-- not promoted (computed)
    val EXCLUDED: IntArray = intArrayOf(1, 2, 3) // <-- not promoted (array)
}
```

Three different reasons three different fields stayed `val`:

- `BASE_PATH` is the surprising one. The RHS is a plain string literal,
  the field is final, the enclosing scope is now `object`. `const val`
  would compile. J2K skips it. This is the case my proposed fix targets.
- `COMPUTED` is an arithmetic expression. Const-eligible in Kotlin (the
  compiler folds `1 + 2`), but J2K can't tell without compile-time
  evaluation. Defensible omission.
- `EXCLUDED` is an array. Cannot be `const` in Kotlin (arrays aren't
  compile-time constants). Correct.

## 3. Anonymous Runnable -- still `object :`, not lambda

**Java input** (`edge-cases/01_anonymous_runnable/Sample.java`):

```java
public class Sample {
    private final Runnable hook = new Runnable() {
        @Override public void run() { System.out.println("hook fired"); }
    };
    public Runnable get() { return hook; }
}
```

**J2K output**:

```kotlin
class Sample {
    private val hook: Runnable = object : Runnable {
        override fun run() {
            println("hook fired")
        }
    }
    fun get(): Runnable? { return hook }
}
```

`Runnable` is a SAM, the body is one method, the body has no
self-reference. This *could* lift to `Runnable { println("hook fired") }`
-- the kind of thing the IDE's intention "Convert anonymous object to
lambda" does interactively. The `FunctionalInterfacesConversion` pass
in J2K fires on the lambda-receiver side only when the target type is
already declared `fun interface` (case 4 below shows the lift is gated
on `@FunctionalInterface` on the Java source). `Runnable` from
`java.lang` was not marked `@FunctionalInterface` until Java 8
introduced the annotation, but `java.lang.Runnable` is annotated. So
the surprise here is that J2K still doesn't lift the field initializer
even when the target *is* a SAM-eligible type. Whether that's a J2K
bug or a deliberate under-promotion to keep field-initialisation order
obvious -- open question.

The other interesting bit: the return type of `get()` came out as
`Runnable?`. The Java field is `final` and assigned at declaration; it
is never null. J2K's nullability inferrer over-conserves at the API
surface here.

## 4. `fun interface` only fires when `@FunctionalInterface` is on the Java source

**Java** (`fixtures/newj2k/functionalInterfaces/NoFunctionalInterfaceAnnotation/NoFunctionalInterfaceAnnotation.java`):

```java
public interface MyRunnable {
    void run();
}
```

**Authentic J2K output**:

```kotlin
interface MyRunnable {
    fun run()
}
```

Plain `interface`. No `fun` keyword. The companion fixture
(`functionalInterfaces/MyRunnable/`) is byte-for-byte the same Java
source plus a `@FunctionalInterface` annotation; that one converts to
`fun interface MyRunnable { fun run() }`. So the rule J2K's
`FunctionalInterfacesConversion` follows is "lift only when the
annotation is present", not "lift any single-abstract-method shape."

Two expectations encode this in `fixtures/newj2k/expectations.txt`:

```
functionalInterfaces/MyRunnable/MyRunnable.kt
    | fun_interface_promoted | yes | fun interface MyRunnable
functionalInterfaces/NoFunctionalInterfaceAnnotation/NoFunctionalInterfaceAnnotation.kt
    | fun_interface_NOT_promoted_without_annotation | no | fun\s+interface
```

The `no` row is doing the real work. If a future J2K bump starts
lifting unannotated SAMs, the eval prints the regex match and exits 3.

Why this is sound: every `interface` in Java with one abstract method
is technically a SAM, but Java callers can pass `new Foo() { ... }`
anonymous-class instances. Lifting to `fun interface` would change the
observable Kotlin API for downstream Java callers -- you can no longer
pass an arbitrary subclass instance where a `fun interface` is
expected, the call site coerces lambda literals only. The annotation
is the source author's signal that the type is meant for lambda use,
and J2K respects that signal. I had this case the other way around in
an earlier draft of the doc; the executable check is what caught it.

## 5. Try-with-resources to `.use {}`, including the multi-resource case

**Java** (`edge-cases/07_try_with_resources/Sample.java`):

```java
public String readTwo(String pathA, String pathB) throws IOException {
    try (BufferedReader a = new BufferedReader(new FileReader(pathA));
         BufferedReader b = new BufferedReader(new FileReader(pathB))) {
        return a.readLine() + b.readLine();
    }
}
```

**J2K output**:

```kotlin
@Throws(IOException::class)
fun readTwo(pathA: String?, pathB: String?): String? {
    BufferedReader(FileReader(pathA)).use { a ->
        BufferedReader(FileReader(pathB)).use { b ->
            return a.readLine() + b.readLine()
        }
    }
}
```

Multi-resource try gets nested `.use {}` calls. Reads naturally. The
two parameter types coming back as `String?` is the same nullability
over-conservation as case 3 -- the params *can* be null in Java, J2K
preserves that conservatively. A human would write `String` (no `?`)
if the contract is "non-null path". Tradeoff: defensive vs idiomatic.
J2K picks defensive, which I think is the right call for a converter
that can't see the calling code.
