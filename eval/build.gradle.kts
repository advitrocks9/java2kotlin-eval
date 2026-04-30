plugins {
    kotlin("jvm")
    application
}

dependencies {
    // kotlin-compiler-embeddable bundles a self-contained PSI/AST. Used here
    // for KtFile traversal -- regex was the placeholder; PSI is the real read.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    // io.github.java-diff-utils for unified-diff hunks. Used by the
    // baseline-diff pass that compares a candidate .kt corpus against a
    // reference one (e.g. LLM output vs JetBrains testData baselines).
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    // JavaParser for input-side metrics (recall: did J2K miss any
    // try-with-resources / vararg / static final the .java input had?).
    implementation("com.github.javaparser:javaparser-core:3.26.2")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("j2keval.MainKt")
}

// run from the root of the project so users can pass relative paths like
// "fixtures/newj2k" without thinking about which subproject they're in.
tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
