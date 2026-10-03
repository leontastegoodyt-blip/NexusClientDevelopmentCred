// =====================================================================================
// mod-legacy/settings.gradle.kts – mod-legacy is its OWN small Gradle build (not a normal module).
//
// Why: Minecraft 1.8.9 mods are built with Legacy Fabric's tool ("legacy-looming"), which needs an
// older version of Fabric's tool (Loom 1.16) than the 26.3 mod uses (Loom 1.18). Two Loom versions
// can't live in one Gradle build, so this folder is a separate build that the main build "includes"
// (see includeBuild in the root settings.gradle.kts). You still run everything with the root gradlew:
//   gradlew :mod-legacy:build        – builds the 1.8.9 mod
//   gradlew :mod-legacy:runClient    – opens Minecraft 1.8.9 with the mod (folder mod-legacy/run)
// =====================================================================================

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.legacyfabric.net/") { name = "Legacy Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads Java 8 automatically for running Minecraft 1.8.9 in development.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mod-legacy"
