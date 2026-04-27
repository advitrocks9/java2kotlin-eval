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

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
