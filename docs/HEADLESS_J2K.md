# Headless J2K — what it took to get the converter running outside the IDE

There is no public CLI for IntelliJ's Java→Kotlin converter. The
[discuss.kotlinlang.org thread](https://discuss.kotlinlang.org/t/how-to-convert-java-source-files-into-kotlin-in-an-existing-project-using-command-line/1507)
on the topic ends in "Java to Kotlin conversion cannot be implemented
correctly outside of IntelliJ IDEA." Meta worked around this by writing a
custom plugin -- they describe it in [Translating Java to Kotlin at Scale](https://engineering.fb.com/2024/12/18/android/translating-java-to-kotlin-at-scale/)
as "an IntelliJ plugin that includes a class extending `ApplicationStarter`
and calling directly into the `JavaToKotlinConverter` class." That's the
shape this plugin uses.

## The path that worked, after a few that didn't

**1. `JavaToKotlinAction.Handler.convertFiles` -- doesn't return.**

Handler is the same code path the IDE menu invokes. With a real JDK and
source root attached, the call enters `withModalProgress(project, ...)`
which spins waiting for a UI dialog that never materialises in headless.
I let it run for 11 minutes before killing it. The convertFiles signature
is `suspend`, so even though `runBlocking` would let me await it, the
internal modal-progress block keeps waiting on a Swing event that never
arrives.

**2. Bypass Handler, call `NewJavaToKotlinConverter.elementsToKotlin`
directly -- this works.**

`elementsToKotlin(List<PsiElement>)` is the synchronous lower-level
entry point. It returns a `Result` whose `results: List<ElementResult?>`
lines up with the input list. Each `ElementResult.text` is the converted
Kotlin source. We lose the IDE-side post-processing pass (rename
refactoring + a couple of cosmetic passes) but the structural metrics the
eval cares about are unchanged.

**3. The project needs a real module, not just an opened directory.**

`ProjectManager.loadAndOpenProject` is deprecated and silently returns the
already-open project on the second invocation. Switched to
`ProjectManagerEx.openProject(workRoot, OpenProjectTask{...})` which is
the modern API. Even after that, opening a directory with no `.iml` gives
you a project with zero modules; J2K's `targetKaModule` resolution then
returns null and the converter produces an empty result. Fix: create a
module in-memory with `ModuleManager.getModifiableModel().newModule(...)`
inside a write action. Module type `JAVA_MODULE`.

**4. JDK creation is a write op.**

`JavaSdk.createJdk(name, javaHome, isJre=false)` walks the JDK's class
roots to build the SDK's class index. That's writing project state, so
the call has to live inside `runWriteAction`, not `runReadAction` (which
is what I had at first -- it logged a SEVERE stack trace per file and
returned an unusable SDK).

**5. `ApplicationStarter.main` is on EDT under a write-intent context.**

So `runWriteAction { ... }` works directly without `invokeAndWait`
wrappers. Wrapping in `invokeAndWait` from EDT to EDT deadlocks instead.
This was the second hardest thing to find -- the symptom was the JVM
exiting cleanly between two log lines with no stack trace, because
`ApplicationStarter` swallows uncaught throwables. Workaround: catch in
`main` and print before `exitProcess(1)`.

## The full minimum recipe

```kotlin
// 1. open project via ProjectManagerEx (not the deprecated ProjectManager)
val project = ProjectManagerEx.getInstanceEx()
    .openProject(workRoot, OpenProjectTask { isNewProject = false })

// 2. inside a write action, create a module if there isn't one
val module = runWriteAction {
    val mmm = ModuleManager.getInstance(project).getModifiableModel()
    val mod = mmm.newModule(workRoot.resolve("module.iml").toString(),
                            ModuleTypeManager.getInstance().findByID("JAVA_MODULE").id)
    mmm.commit(); mod
}

// 3. inside a write action, register a JDK and assign it to the project
val sdk = runWriteAction {
    val s = JavaSdk.getInstance().createJdk("auto-jdk-21", System.getProperty("java.home"), false)
    ProjectJdkTable.getInstance().addJdk(s); s
}
runWriteAction { ProjectRootManager.getInstance(project).projectSdk = sdk }

// 4. inside a write action, attach the source root to the module
runWriteAction {
    val rootModel = ModuleRootManager.getInstance(module).modifiableModel
    val ce = rootModel.addContentEntry(srcVf)
    ce.addSourceFolder(srcVf, false)
    rootModel.inheritSdk(); rootModel.commit()
}

// 5. NOT through Handler.convertFiles; call elementsToKotlin directly
val converter = NewJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
val result = converter.elementsToKotlin(psiJavaFiles)
// result.results[i] is ElementResult? for psiJavaFiles[i]
```

## Things I'd still want to fix

- The `withModalProgress` wrapper on `filesToKotlin` exists for a reason:
  the IDE post-processor includes some passes (rename collisions, content
  re-formatting) that polish the converter output. By bypassing it I get
  rougher Kotlin. The right fix is probably running the post-processor
  directly with a non-modal progress indicator -- I haven't tried.
- The created JDK is registered in the table for the lifetime of the
  process. A real CLI would unregister it on shutdown to keep the user's
  JDK list clean.
- Module creation doesn't include external deps. If the input Java imports
  a library, J2K can't resolve those types and falls back to platform
  types in the output. For JCommander this rarely matters (no third-party
  imports in main); for a Spring app it would.
