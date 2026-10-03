// =====================================================================================
// mod-legacy – the Nexus mod for Minecraft 1.8.9, on Legacy Fabric.
//
// (The plan used to be Forge 1.8.9, but Nexus's launcher plays 1.8.9 with Legacy Fabric, so the
// mod is a Legacy Fabric mod. It doesn't need Legacy Fabric API – everything hooks in with mixins –
// so the player only needs this one file.)
//
// Minecraft 1.8.9's code has scrambled names, so this uses Legacy Fabric's readable names
// ("yarn" mappings), e.g. MinecraftClient, InGameHud, TextRenderer. When the mod is built, the
// "remapJar" step turns them back into the names the real game uses.
//
// mod-core's code (menus, HUD mods, settings) is compiled straight into this mod. It is Java 8
// code, so it runs on 1.8.9's Java 8.
// =====================================================================================

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.16.1"
    id("legacy-looming") version "1.16.1" // must be the same version as fabric-loom-remap
}

group = "com.nexusclient"
version = "0.1.0"

val minecraftVersion = property("minecraft_version") as String

// Build with a modern JDK, but make Java 8 class files (1.8.9 runs on Java 8).
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options") // (hide the "Java 8 is old" warning)
}

// mod-core's source code is shared: compile it straight into this mod.
sourceSets {
    named("main") {
        java.srcDir("../mod-core/src/main/java")
        resources.srcDir("../mod-core/src/main/resources")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(legacy.yarn(minecraftVersion, property("yarn_build") as String))
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    // (Gson, which mod-core uses, already comes with Minecraft 1.8.9.)
}

// Test run: gradlew :mod-legacy:runClient opens Minecraft 1.8.9 with the mod (in mod-legacy/run).
// Add -PnexusSelfTest to have it open the menus, screenshot them and close by itself.
loom {
    runs {
        named("client") {
            if (project.hasProperty("nexusSelfTest")) {
                vmArg("-Dnexus.selftest=true")
            }
        }
    }
}

// Run the test game on Java 8, like real players' 1.8.9.
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
}

// Put the version number into fabric.mod.json.
tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.named<org.gradle.jvm.tasks.Jar>("remapJar") {
    archiveBaseName.set("nexus-client-legacy-$minecraftVersion")
}
