// Pin the Kotlin Gradle plugin once at the root so the two subprojects don't
// each pull a different copy of org.jetbrains.kotlin.jvm.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.intellij.platform") version "2.2.1" apply false
}
