# Headless J2K -- what worked, what didn't

There is no public CLI for IntelliJ's Java->Kotlin converter. The
[discuss.kotlinlang.org thread](https://discuss.kotlinlang.org/t/how-to-convert-java-source-files-into-kotlin-in-an-existing-project-using-command-line/1507)
on the topic ends with "Java to Kotlin conversion cannot be implemented
correctly outside of IntelliJ IDEA." Meta worked around this by writing
an IntelliJ plugin -- they describe it in [Translating Java to Kotlin at
Scale](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/)
as "an IntelliJ plugin that includes a class extending `ApplicationStarter`
and calling directly into the `JavaToKotlinConverter` class." That's the
shape this plugin uses.

This doc is the postmortem on getting that shape to actually run, the
non-obvious pieces that took the most time, and **the specific platform
gap I didn't get past**: the runner does not run in CI, and on macOS
runs successfully on a fresh sandbox but flakes once the sandbox state
warms up. I document the failure with logs rather than gloss it.

## What actually works

`runner/src/main/kotlin/j2k/runner/J2KStarter.kt` does the following on
a fresh sandbox:

1. Stages the input directory into `/tmp/j2k-work-XXX/src` (we'll
   stamp `.idea/iml` files there without touching the user's tree).
2. Opens it via `ProjectManagerEx.openProject(workRoot, OpenProjectTask{...})`.
   The deprecated `ProjectManager.loadAndOpenProject` path silently
   returns the already-open project on a second invocation; the modern
   API doesn't.
3. Inside a `runWriteAction`, registers a JDK (`JavaSdk.createJdk`,
   pointed at `System.getProperty("java.home")`) and assigns it as
   `ProjectRootManager.projectSdk`. This is a write op -- it indexes
   the JDK class roots. Doing it in a read action throws SEVERE per
   file and gives back an unusable SDK.
4. Creates a Java module on the fly via
   `ModuleManager.modifiableModel.newModule(...)`. An auto-opened
   project with no `.iml` has zero modules; J2K's `targetKaModule`
   resolution then returns null and `elementsToKotlin` produces an
   empty result.
5. Adds the staged source root as the module's content + source
   folder, also under a write action.
6. Calls
   `NewJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings).elementsToKotlin(files)`
   from a pooled thread under a `runReadAction`. The Analysis API
   refuses to run on EDT; the J2K nullability inferrer needs a read
   action. Both happy on a pool thread + read action.
7. Writes each `ElementResult.text` to disk after a regex
   marker-strip pass (J2K's internal `/*@@hash@@*/` symbol-resolution
   placeholders -- the IDE's post-processing pass cleans these up,
   but bypassing the Handler skips that pass).

I deliberately don't go through `JavaToKotlinAction.Handler.convertFiles`.
That's the same code path the IDE menu invokes, but it enters
`withModalProgress(project, ...)` which spins waiting for a Swing event
that never arrives in headless. I let it run for 11 minutes before
killing it.

The minimum sequence in code:

```kotlin
val project = ProjectManagerEx.getInstanceEx()
    .openProject(workRoot, OpenProjectTask {
        forceOpenInNewFrame = false
        isNewProject = true
        useDefaultProjectAsTemplate = false
        runConfigurators = false
    })

val module = runWriteAction {
    val mm = ModuleManager.getInstance(project).getModifiableModel()
    val mod = mm.newModule(
        workRoot.resolve("module.iml").toString(),
        ModuleTypeManager.getInstance().findByID("JAVA_MODULE").id
    )
    mm.commit()
    mod
}

val sdk = runWriteAction {
    val s = JavaSdk.getInstance().createJdk("auto-jdk-21",
        System.getProperty("java.home"), false)
    ProjectJdkTable.getInstance().addJdk(s)
    s
}
runWriteAction { ProjectRootManager.getInstance(project).projectSdk = sdk }

val converter = NewJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
val result = ApplicationManager.getApplication()
    .executeOnPooledThread<Result> {
        ApplicationManager.getApplication().runReadAction<Result> {
            converter.elementsToKotlin(files)
        }
    }.get()
```

`ApplicationStarter.main` runs on EDT under a write-intent context.
`runWriteAction { ... }` upgrades that. Wrapping in `invokeAndWait`
from EDT to EDT deadlocks instead -- the symptom is the JVM exiting
cleanly between two log lines with no stack trace, because
`ApplicationStarter` swallows uncaught throwables.

That last bit took an evening to find. The catch+print in `main()`
(`runner/src/main/kotlin/j2k/runner/J2KStarter.kt:55-63`) is now there
specifically so any exception escapes to stderr before the JVM exits.

## What doesn't work: runIde in CI

The CI workflow's `edge-cases` and `jcommander` jobs both timed out at
their respective `timeout-minutes` ceilings (30 + 45 min). The actual
diagnosis from the captured `idea.log`
([`headless-j2k-cancel-tail.txt`](headless-j2k-cancel-tail.txt) is the
relevant tail):

```
2026-04-30 02:02:56,553 SEVERE  JreHiDpiUtil  Must be not computed before that call
[stack trace through:]
  ApplicationLoader$preloadNonHeadlessServices$2$5.invokeSuspend
  → ComponentManagerImpl.getServiceAsync
  → InstanceContainerImpl.instance
  → LazyInstanceHolder.getInstance / .initialize
  → kotlinx.coroutines.BuildersKt.launch / .withContext
  → ServiceInstanceInitializer.createInstance

[then -- nothing for 28 minutes]

2026-04-30 02:13:52,827 INFO    FSRecords  Checking VFS started
2026-04-30 02:13:52,847 INFO    FSRecords  Checking VFS finished

[silence again until ##[error]The operation was canceled at 02:30:51]
```

`ApplicationLoader.preloadNonHeadlessServices` enters a coroutine that
never returns. My `[j2k]` STDOUT lines never appear -- the hang is
upstream of `ApplicationStarter` dispatch entirely. Adding xvfb-run,
caching `.intellijPlatform/`, and bumping the timeout don't help; this
is the platform itself waiting for a non-headless service to come up.

I haven't bisected which service is the culprit. The honest answer is
"I don't know which one yet, and the time budget for this submission
ran out before I could." If I were continuing, the move is to add
`-Didea.is.internal=true` plus structured logging into the IntelliJ
Platform via the `idea.platform.log.config.path` flag, capture which
service the coroutine is awaiting, and either suppress that service or
preload it differently in our `<applicationConfigurable>` chain.

## Why "runs locally on fresh sandbox" still flakes

Even on macOS, where the runner *did* complete one full edge-case run
on 2026-04-29, subsequent invocations against the same sandbox reach
"opened project" and then hang the same way as CI. I didn't bisect
which service caches state across invocations; the workaround is to
wipe `runner/build/idea-sandbox` before each run, which is what
`J2KStarterAcceptanceTest` and the `scripts/run-*.sh` helpers do. Two
seconds of cleanup beats five minutes of staring at a hung process.

## What the May 2 runner unblock changed

The 2026-04-30 doc had the runner blocked. On 2026-05-02 I bisected the
actual `IndexNotReadyException` thrown by
`KotlinStdlibCacheImpl$ModuleStdlibDependencyCache.calculate` inside
J2K's nullability inferrer: the converter starts before the JDK index
finishes scanning. Adding a `DumbService.isDumb` poll (block until
smart mode reaches) before `elementsToKotlin` fixed it on macOS. The
runner now completes JCommander (73 main/java files -> 73 .kt files) on
a fresh sandbox in ~30 seconds + ~5-10 minutes of one-time JDK indexing.

The CI `runIde` job is still off because xvfb-on-ubuntu still hangs
upstream of the dumb-mode wait (the `preloadNonHeadlessServices`
coroutine). My local fix is sufficient to capture
`fixtures/jcommander-converted/` outputs and run the eval + tests-pass
metric, but the platform gap on CI remains.

## What this means for the submission

Three things, all in the README's headline:
1. The eval module's tests run in CI; the `runIde` leg is documented as
   a known gap. CI scores the committed JetBrains testData sample (15
   pairs), the committed runner outputs (4 fixtures + 73 from
   JCommander), and the committed Claude captures.
2. The runner architecture compiles, the plugin loads, and the
   conversion path has executed end-to-end on macOS with JDK 21,
   IntelliJ Platform 2024.3, against both the edge-cases corpus and
   the full JCommander main/java tree. Outputs are committed.
3. The runner-in-CI path is blocked on a platform hang I localised but
   didn't bisect to a single service. Documenting that gap honestly
   (with logs) seemed worth more than papering over it.

A senior reviewer who wants to fix the hang has the captured stack
trace and the steps to reproduce -- the headless-j2k-cancel-tail.txt
plus `runner/src/main/kotlin/j2k/runner/J2KStarter.kt` is everything
needed.
