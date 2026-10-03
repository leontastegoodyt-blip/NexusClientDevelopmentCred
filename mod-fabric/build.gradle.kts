// =====================================================================================
// mod-fabric – the Nexus mod for Fabric (Minecraft 26.3 for now; 1.20.x comes later).
//
// It uses Fabric's build tool "Loom", which downloads Minecraft and sets it up so our code can
// use it. Minecraft 26.x ships with readable names, so no "mappings" are needed.
// This module is the thin layer between Minecraft and mod-core (where the features live).
// =====================================================================================

plugins {
    id("net.fabricmc.fabric-loom") version "1.18.2"
}

// Minecraft 26.x needs Java 25 (Gradle downloads it automatically if this PC doesn't have it).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

val minecraftVersion = property("minecraft_version") as String
val loaderVersion = property("loader_version") as String
val fabricApiVersion = property("fabric_api_version") as String

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    // Fabric API: shared hooks (key bindings, HUD drawing, tick events) that most mods use.
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    // The shared Nexus code.
    implementation(project(":mod-core"))
}

// Test run: ./gradlew :mod-fabric:runClient opens Minecraft with the mod (in mod-fabric/run).
// Add -PnexusSelfTest to have it open the menus, screenshot them and close by itself.
loom {
    runs {
        named("client") {
            if (project.hasProperty("nexusSelfTest")) {
                vmArg("-Dnexus.selftest=true")
                vmArg("-Dnexus.dfr.force=true")
            }
        }
    }
}

// Put the version number into fabric.mod.json.
tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

// Pack mod-core's code inside the mod's .jar, so players only need one file.
tasks.jar {
    from(project(":mod-core").extensions.getByType<SourceSetContainer>()["main"].output)
    archiveBaseName.set("nexus-client-fabric-$minecraftVersion")
}
