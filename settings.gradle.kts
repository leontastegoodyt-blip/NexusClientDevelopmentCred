// =====================================================================================
// settings.gradle.kts – the "table of contents" for the whole Nexus project.
//
// Gradle reads this file first. It tells Gradle:
//   1. what the project is called
//   2. which folders (called "modules") are part of it
// If you ever add a new module folder, you must also add it to the list below.
// =====================================================================================

// Where Gradle finds build plugins. Fabric's plugin ("Loom") lives on Fabric's own server.
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle automatically download the right Java version when a module asks for one
    // it can't find on this PC (for example Java 25 for Minecraft 26.x, or Java 8 for 1.8.9).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nexus-client"

include(
    "launcher",   // the desktop app players open (JavaFX)
    "mod-core",   // shared in-game mod code: HUD, settings, DFR logic
    "mod-fabric", // the mod for Fabric (Minecraft 1.20.x + latest)
    "mod-forge",  // the mod for Forge (Minecraft 1.20.x + latest)
    "backend",    // the online server: friends, chat, parties, cosmetics
)

// The Nexus mod for Fabric on Minecraft 1.20–1.21.x: one small build per group of versions whose
// code is the same, all in mod-fabric-modern/ (shared code in mod-fabric-modern/common).
// Run one with e.g. gradlew :mod-fabric-1.20.1:runClient
for (version in listOf("1.20.1", "1.20.4", "1.20.6", "1.21.4", "1.21.11")) {
    include("mod-fabric-$version")
    project(":mod-fabric-$version").projectDir = file("mod-fabric-modern/$version")
}

// The 1.8.9 mod (Legacy Fabric) is its own small build, because its build tool needs an older
// Fabric Loom than mod-fabric uses (see mod-legacy/settings.gradle.kts). Its tasks still run from
// here, e.g. gradlew :mod-legacy:build
// The Nexus mod for Fabric on the older 26.x versions (same code as 26.3 in mod-fabric, plus a few
// replaced files in mod-fabric/<version>/src).
for (version in listOf("26.2", "26.1.2")) {
    include("mod-fabric-$version")
    project(":mod-fabric-$version").projectDir = file("mod-fabric/$version")
}

// The Nexus mod for Forge on the older 26.x versions (the Fabric 26.x code + the Forge start-up class).
for (version in listOf("26.2", "26.1.2")) {
    include("mod-forge-$version")
    project(":mod-forge-$version").projectDir = file("mod-forge/$version")
}

// The Nexus mod for Forge on Minecraft 1.20.1 (the 1.20.1 Fabric code, renamed to Forge's names).
include("mod-forge-1.20.1")
project(":mod-forge-1.20.1").projectDir = file("mod-forge/1.20.1")

// The Nexus mod for Forge on 1.20.4, 1.20.6, 1.21.4 and 1.21.11 (the Fabric code for the same version +
// the Forge start-up class in mod-forge/modern; 1.20.4 is renamed to Forge's names like 1.20.1).
for (version in listOf("1.20.4", "1.20.6", "1.21.4", "1.21.11")) {
    include("mod-forge-$version")
    project(":mod-forge-$version").projectDir = file("mod-forge/$version")
}

includeBuild("mod-legacy")
