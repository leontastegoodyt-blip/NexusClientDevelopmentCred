// =====================================================================================
// One Minecraft-version build of the Nexus mod for Fabric (the version is in gradle.properties
// next to this file). Every version folder in mod-fabric-modern has this same file.
//
// Minecraft 1.20.x–1.21.x ship with scrambled names, so this uses Mojang's official names
// ("mojmap" – the same names 26.3 uses) and "remapJar" turns them back for the real game.
//
// The code comes from three places:
//   ../common/src/main/java  – code that's the same on every 1.20–1.21 version
//   src/main/java            – only what's different on this version
//   mod-core                 – the menus, HUD mods and settings shared by ALL versions
// =====================================================================================

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val minecraftVersion = property("minecraft_version") as String
val javaVersion = (property("java_version") as String).toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion) // 1.20–1.20.4 need Java 17, newer versions Java 21
}

// Extra shared folders this version uses (gradle.properties "shared_folders"), e.g. "classic" for
// the 1.20–1.21.4 builds, which share the older key/mouse/HUD code.
val sharedFolders = listOf("common") +
    (findProperty("shared_folders") as String? ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }

sourceSets {
    named("main") {
        for (folder in sharedFolders) {
            java.srcDir("../$folder/src/main/java")
            resources.srcDir("../$folder/src/main/resources")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation(project(":mod-core"))
}

// Test run: gradlew :mod-fabric-<version>:runClient (game folder: run/ next to this file).
// Add -PnexusSelfTest to have it open the menus, join a test world, press Right Shift, screenshot and close.
loom {
    runs {
        named("client") {
            if (project.hasProperty("nexusSelfTest")) {
                vmArg("-Dnexus.selftest=true")
            }
        }
    }
}

// Fill in fabric.mod.json (in ../common) for this version.
tasks.processResources {
    val values = mapOf(
        "version" to project.version.toString(),
        "minecraft_range" to project.property("minecraft_range") as String,
        "java_version" to javaVersion.toString(),
    )
    inputs.properties(values)
    filesMatching("fabric.mod.json") {
        expand(values)
    }
}

// Pack mod-core's code inside the mod's .jar, so players only need one file.
tasks.jar {
    from(project(":mod-core").extensions.getByType<SourceSetContainer>()["main"].output)
}
tasks.named<org.gradle.jvm.tasks.Jar>("remapJar") {
    archiveBaseName.set("nexus-client-fabric-$minecraftVersion")
}
