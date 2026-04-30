plugins {
    kotlin("jvm")
    application
}

dependencies {
    // No SDK -- the Anthropic messages API is a single HTTPS POST. Pulling in
    // a multi-MB SDK for one endpoint is overkill. java.net.http handles it.
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("j2k.llm.MainKt")
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
