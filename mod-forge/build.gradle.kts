// =====================================================================================
// mod-forge – the Nexus mod for Forge on Minecraft 26.3.
//
// Minecraft 26.x isn't scrambled ("obfuscated"), so Forge runs it with the very same names as
// Fabric. That means this mod is simply the Fabric 26.3 mod's code (mod-fabric, mixins included)
// plus one Forge start-up class (NexusForge) instead of the Fabric one (NexusFabric). No Forge
// build tool is needed: Loom only provides Minecraft's classes to compile against, and Forge's own
// classes come from Forge's Maven server (compile-only – Forge itself provides them in the game).
//
// Forge's Mixin (0.8.7) only understands classes up to Java 21, so the mixin classes are marked
// as Java 21 in the finished jar (they use nothing newer). Everything else stays Java 25.
// =====================================================================================

plugins {
    id("net.fabricmc.fabric-loom") version "1.18.2"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

val minecraftVersion = "26.3"
val forgeVersion = "26.3-66.0.10"

repositories {
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
    maven("https://repo.spongepowered.org/repository/maven-public/") { name = "Sponge" }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // Forge's own code – only to compile against (the game already has it).
    for (artifact in listOf(
        "net.minecraftforge:forge:$forgeVersion:universal",
        "net.minecraftforge:fmlcore:$forgeVersion",
        "net.minecraftforge:fmlloader:$forgeVersion",
        "net.minecraftforge:javafmllanguage:$forgeVersion",
        "net.minecraftforge:eventbus:7.0.6",
        "net.minecraftforge:forgespi:8.0.0",
        "org.spongepowered:mixin:0.8.7",
        "io.github.llamalad7:mixinextras-common:0.5.4",
    )) {
        compileOnly(artifact) { isTransitive = false }
    }
    compileOnly("org.jspecify:jspecify:1.0.1")
    // The shared Nexus code.
    implementation(project(":mod-core"))
}

// The Fabric 26.3 mod's code and resources, without its Fabric-only parts.
sourceSets.main {
    java {
        srcDir("../mod-fabric/src/main/java")
        exclude("com/nexusclient/fabric/NexusFabric.java")
    }
    resources {
        srcDir("../mod-fabric/src/main/resources")
        exclude("fabric.mod.json")
    }
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("META-INF/mods.toml") {
        expand("version" to modVersion)
    }
    // Forge's Mixin knows Java levels up to 21.
    filesMatching("nexusclient.mixins.json") {
        filter { line -> line.replace("\"JAVA_25\"", "\"JAVA_21\"") }
    }
}

// Mark the mixin classes as Java 21 (class file version 65) so Forge's Mixin accepts them.
val markMixinsJava21 by tasks.registering {
    dependsOn(tasks.compileJava)
    val folder = layout.buildDirectory.dir("classes/java/main/com/nexusclient/fabric/mixin")
    doLast {
        folder.get().asFile.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { file ->
            val bytes = file.readBytes()
            bytes[6] = 0
            bytes[7] = 65
            file.writeBytes(bytes)
        }
    }
}

tasks.jar {
    dependsOn(markMixinsJava21)
    from(project(":mod-core").extensions.getByType<SourceSetContainer>()["main"].output)
    archiveBaseName.set("nexus-client-forge-$minecraftVersion")
    manifest {
        attributes("MixinConfigs" to "nexusclient.mixins.json")
    }
}
