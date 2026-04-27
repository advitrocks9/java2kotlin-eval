plugins {
    kotlin("jvm") version "2.0.21"
    application
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("j2keval.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
