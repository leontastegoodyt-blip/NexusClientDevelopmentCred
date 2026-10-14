// =====================================================================================
// One older-26.x build of the Nexus mod for Forge (the versions are in gradle.properties next to
// this file). Every 26.x version folder inside mod-forge has this same file.
//
// Like the Forge 26.3 build (mod-forge), the code is the Fabric mod's: mod-fabric/src, with the files
// this Minecraft version replaces (mod-fabric/<version>/src), minus the Fabric start-up class,
// plus the Forge start-up class (mod-forge/src). Files in THIS folder's src/ replace any of those.
// See mod-forge/README.md for why the mixin classes are marked as Java 21.
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

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String

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
    implementation(project(":mod-core"))
}

// Where the code comes from, most specific first: this folder, the Fabric version folder, then the
// shared Fabric code and the Forge start-up class. A file found earlier replaces the same file later.
val layers = listOf(file("src/main/java"), rootProject.file("mod-fabric/$minecraftVersion/src/main/java"),
        rootProject.file("mod-forge/src/main/java"), rootProject.file("mod-fabric/src/main/java"))
fun pathsIn(dir: File) = if (dir.isDirectory) fileTree(dir).files.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet() else emptySet()

sourceSets.main {
    java {
        setSrcDirs(emptyList<File>())
        for ((index, dir) in layers.withIndex()) {
            srcDir(dir)
            val earlier = layers.take(index).flatMap { pathsIn(it) }.toSet()
            exclude { element -> !element.isDirectory && element.file.startsWith(dir) && element.relativePath.pathString in earlier }
        }
        exclude("com/nexusclient/fabric/NexusFabric.java")
    }
    resources {
        setSrcDirs(listOf(file("src/main/resources"), rootProject.file("mod-forge/src/main/resources"),
                rootProject.file("mod-fabric/src/main/resources")))
        exclude("fabric.mod.json")
    }
}
// Same-named resources (mods.toml, pack.mcmeta): the first one wins.
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("META-INF/mods.toml") {
        expand("version" to modVersion)
        filter { line -> line.replace("[26.3,26.4)", "[$minecraftVersion,$minecraftVersion.99]") }
    }
    filesMatching("nexusclient.mixins.json") {
        filter { line -> line.replace("\"JAVA_25\"", "\"JAVA_21\"") }
    }
}

// Mark the mixin classes as Java 21 (class file version 65) so Forge's Mixin accepts them.
val markMixinsJava21 = tasks.register("markMixinsJava21") {
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
