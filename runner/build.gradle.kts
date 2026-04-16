// IntelliJ-Platform plugin -- ApplicationStarter that drives JavaToKotlinConverter.
// Real headless J2K, the same pattern Meta describes in their Kotlinator post.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("243")
            untilBuild.set("251.*")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
