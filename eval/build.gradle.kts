plugins {
    kotlin("jvm")
    application
}

dependencies {
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
