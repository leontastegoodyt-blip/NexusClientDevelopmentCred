// =====================================================================================
// One 1.20.2–1.21.11 build of the Nexus mod for Forge (the versions are in gradle.properties next to
// this file). The 1.20.4, 1.20.6, 1.21.4 and 1.21.11 folders inside mod-forge all have this same file.
// (1.20.1 has its own older file; 26.x builds are in mod-forge/<26.x version>.)
//
// The code is the Fabric build's for the same version (mod-fabric-modern: common + the shared folders
// + the version folder) minus its three Fabric-only files, plus the Forge start-up class
// (mod-forge/modern). Files in THIS folder's src/ replace any file with the same path.
//
// Forge runs these versions with one of two kinds of names:
//   - 1.20.4: Mojang's class names but scrambled method/field names ("SRG"). Like 1.20.1, the
//     finished jar is renamed with "remapForgeJar" (gradle.properties: mcp_config).
//   - 1.20.6 and newer: Mojang's names, the ones we write the code with – no renaming needed.
// =====================================================================================

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import java.nio.file.Files
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val minecraftVersion = property("minecraft_version") as String
val minecraftRange = property("minecraft_range") as String
val forgeVersion = property("forge_version") as String
// The Fabric build this one reuses (and its settings: Java version, shared code folders).
val fabricFolder = rootProject.file("mod-fabric-modern/${property("fabric_build")}")
val fabricProperties = Properties().apply { File(fabricFolder, "gradle.properties").reader().use { load(it) } }
val javaVersion = fabricProperties.getProperty("java_version").toInt()
val sharedFolders = listOf("common") + fabricProperties.getProperty("shared_folders", "")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
val mcpConfigVersion = findProperty("mcp_config") as String? // only for versions with SRG names
// Forge's Mixin library: 0.8.5 (Forge 1.20.x) knows Java levels up to 18, 0.8.7 up to 21.
val mixinJava = (findProperty("mixin_java") as String?)?.toInt()
val packMixinExtras = (findProperty("pack_mixinextras") as String?).toBoolean()
val mixinExtrasVersion = "0.5.4"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

repositories {
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
}

// Where the code comes from, most specific first. A file found earlier replaces the same file later.
val modern = rootProject.file("mod-fabric-modern")
val layers = listOf(file("src/main/java"), rootProject.file("mod-forge/modern/src/main/java"),
        File(fabricFolder, "src/main/java")) + sharedFolders.map { File(modern, "$it/src/main/java") }
fun pathsIn(dir: File) = if (dir.isDirectory) fileTree(dir).files.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet() else emptySet()

sourceSets.main {
    java {
        setSrcDirs(emptyList<File>())
        for ((index, dir) in layers.withIndex()) {
            srcDir(dir)
            val earlier = layers.take(index).flatMap { pathsIn(it) }.toSet()
            exclude { element -> !element.isDirectory && element.file.startsWith(dir) && element.relativePath.pathString in earlier }
        }
        // The Fabric-only files.
        exclude("com/nexusclient/modern/NexusModern.java", "com/nexusclient/modern/FabricHooks.java",
                "com/nexusclient/modern/FabricNetworking.java")
    }
    resources {
        setSrcDirs(listOf(file("src/main/resources"), rootProject.file("mod-forge/modern/src/main/resources"),
                File(fabricFolder, "src/main/resources")) + sharedFolders.map { File(modern, "$it/src/main/resources") })
        exclude("fabric.mod.json")
    }
}

// MixinExtras for Forge, packed inside our jar (Forge only includes it itself from 1.21.6).
val packedInside: Configuration = configurations.create("packedInside") { isTransitive = false }
// MCPConfig: Forge's list of "official → SRG" names (only for versions with SRG names).
val mcpConfig: Configuration = configurations.create("mcpConfig") { isTransitive = false }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    // Forge's own code and the Mixin library – only to compile against (the game already has them).
    for (artifact in listOf(
        "net.minecraftforge:forge:$forgeVersion:universal",
        "net.minecraftforge:fmlcore:$forgeVersion",
        "net.minecraftforge:fmlloader:$forgeVersion",
        "net.minecraftforge:javafmllanguage:$forgeVersion",
        "net.minecraftforge:eventbus:${property("eventbus_version")}",
        "net.minecraftforge:forgespi:${property("forgespi_version")}",
        "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7",
        "io.github.llamalad7:mixinextras-common:$mixinExtrasVersion",
    )) {
        compileOnly(artifact) { isTransitive = false }
    }
    compileOnly("org.jspecify:jspecify:1.0.1")
    if (packMixinExtras) {
        packedInside("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    }
    if (mcpConfigVersion != null) {
        mcpConfig("de.oceanlabs.mcp:mcp_config:$mcpConfigVersion@zip")
    }
    // The shared Nexus code.
    implementation(project(":mod-core"))
}

// Our own mixin config for Forge-only mixins (e.g. the 1.20.6 HUD), if this version has one.
val forgeMixins = file("src/main/resources/nexusclient.forge.mixins.json").exists()

// Same-named resources: the first one wins (this folder → mod-forge/modern → the Fabric build).
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val modVersion = project.version.toString()
    val range = minecraftRange
    val loaderMajor = forgeVersion.substringAfter("-").substringBefore(".")
    inputs.properties(mapOf("version" to modVersion, "range" to range, "loader" to loaderMajor, "mixinJava" to (mixinJava ?: 0)))
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to modVersion, "minecraft_range" to range, "loader_major" to loaderMajor))
    }
    if (mixinJava != null) {
        filesMatching(listOf("nexusclient.mixins.json", "nexusclient.forge.mixins.json")) {
            filter { line -> line.replace(Regex("\"JAVA_\\d+\""), "\"JAVA_$mixinJava\"") }
        }
    }
}

// Mark the mixin classes with the Java level Forge's Mixin understands (see mixin_java).
val markMixins = tasks.register("markMixins") {
    dependsOn(tasks.compileJava)
    val classes = layout.buildDirectory.dir("classes/java/main/com/nexusclient")
    doLast {
        if (mixinJava == null) {
            return@doLast
        }
        val folders = listOf("modern/mixin", "forgemodern/mixin").map { classes.get().asFile.resolve(it) }
        for (folder in folders) {
            folder.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { file ->
                val bytes = file.readBytes()
                bytes[6] = 0
                bytes[7] = (44 + mixinJava).toByte() // class file version = Java version + 44
                file.writeBytes(bytes)
            }
        }
    }
}

// Tells Forge about the packed MixinExtras jar (META-INF/jarjar/metadata.json).
val jarJarMetadata = tasks.register("jarJarMetadata") {
    val file = layout.buildDirectory.file("jarjar/metadata.json")
    outputs.file(file)
    doLast {
        file.get().asFile.writeText("""
            {"jars": [{
              "identifier": {"group": "io.github.llamalad7", "artifact": "mixinextras-forge"},
              "version": {"range": "[$mixinExtrasVersion,)", "artifactVersion": "$mixinExtrasVersion"},
              "path": "META-INF/jarjar/mixinextras-forge-$mixinExtrasVersion.jar",
              "isObfuscated": false
            }]}
        """.trimIndent())
    }
}

tasks.jar {
    dependsOn(markMixins)
    from(project(":mod-core").extensions.getByType<SourceSetContainer>()["main"].output)
    archiveBaseName.set("nexus-client-forge-$minecraftVersion")
    // With SRG renaming, this jar (Mojang's names) is only the input to remapForgeJar below.
    archiveClassifier.set(if (mcpConfigVersion != null) "mojang-names" else "")
    manifest {
        attributes("MixinConfigs" to if (forgeMixins) "nexusclient.mixins.json,nexusclient.forge.mixins.json" else "nexusclient.mixins.json")
    }
    if (packMixinExtras) {
        from(packedInside) { into("META-INF/jarjar") }
        from(jarJarMetadata) { into("META-INF/jarjar") }
    }
}

/** The finished jar: the plain jar for versions with Mojang's names, else the SRG-renamed one. */
val forgeJar = tasks.register("forgeJar") {
    dependsOn(tasks.jar)
    val input = tasks.jar.flatMap { it.archiveFile }
    val output = layout.buildDirectory.file("libs/nexus-client-forge-$minecraftVersion-final.jar")
    inputs.file(input)
    outputs.file(output)
    doLast {
        val outFile = output.get().asFile.toPath()
        Files.deleteIfExists(outFile)
        if (mcpConfigVersion == null) {
            Files.copy(input.get().asFile.toPath(), outFile)
            return@doLast
        }
        // Renames from Mojang's names to Forge's SRG names (see mod-forge/1.20.1/build.gradle.kts):
        //   Mojang name ──(Loom's mappings: named → official)──▶ official ──(MCPConfig: official → SRG)──▶ SRG
        val loom = LoomGradleExtension.get(project)
        val mojang = MemoryMappingTree()
        MappingReader.read(loom.mappingConfiguration.tinyMappings, mojang)
        val srg = MemoryMappingTree()
        ZipFile(mcpConfig.singleFile).use { zip ->
            zip.getInputStream(zip.getEntry("config/joined.tsrg")).reader().use { reader ->
                MappingReader.read(reader, MappingFormat.TSRG_2_FILE, srg)
            }
        }
        val named = mojang.getNamespaceId("named")
        val srgNs = srg.getNamespaceId("srg")
        val mappings = IMappingProvider { out ->
            for (srgClass in srg.classes) {
                val official = srgClass.srcName
                val mojangClass = mojang.getClass(official)
                val namedClass = mojangClass?.getName(named) ?: official
                for (method in srgClass.methods) {
                    val target = method.getName(srgNs) ?: continue
                    val namedName = mojangClass?.getMethod(method.srcName, method.srcDesc)?.getName(named) ?: method.srcName
                    if (namedName == target || namedName.startsWith("<")) {
                        continue
                    }
                    out.acceptMethod(IMappingProvider.Member(namedClass, namedName, mojang.mapDesc(method.srcDesc, named)), target)
                }
                for (field in srgClass.fields) {
                    val target = field.getName(srgNs) ?: continue
                    val mojangField = mojangClass?.getField(field.srcName, null)
                    val namedName = mojangField?.getName(named) ?: field.srcName
                    if (namedName != target) {
                        out.acceptField(IMappingProvider.Member(namedClass, namedName, mojangField?.getDesc(named) ?: "Ljava/lang/Object;"), target)
                    }
                }
            }
        }
        val remapper = TinyRemapper.newRemapper()
            .withMappings(mappings)
            .extension(MixinExtension()) // also renames the names inside @Mixin/@Inject/@At/@Accessor
            .ignoreFieldDesc(true)
            .ignoreConflicts(true)
            .build()
        try {
            OutputConsumerPath.Builder(outFile).build().use { consumer ->
                consumer.addNonClassFiles(input.get().asFile.toPath(), NonClassCopyMode.FIX_META_INF, remapper)
                remapper.readClassPath(*sourceSets.main.get().compileClasspath.files.map { it.toPath() }.toTypedArray())
                remapper.readInputs(input.get().asFile.toPath())
                remapper.apply(consumer)
            }
        } finally {
            remapper.finish()
        }
    }
}

tasks.build {
    dependsOn(forgeJar)
}

// Loom's own Fabric remapping isn't used for Forge.
tasks.named("remapJar") { enabled = false }
