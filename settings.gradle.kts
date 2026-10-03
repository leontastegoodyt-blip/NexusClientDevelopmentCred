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
    "mod-legacy", // the mod for Forge 1.8.9
    "backend",    // the online server: friends, chat, parties, cosmetics
)
