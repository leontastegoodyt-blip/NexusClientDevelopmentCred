// =====================================================================================
// mod-forge-1.20.1 – the Nexus mod for Forge on Minecraft 1.20.1.
//
// The code is the Fabric 1.20.1 build's (mod-fabric-modern: common + classic + v1_20 + 1.20.1),
// minus its three Fabric-only files, plus one Forge start-up class (NexusForge120).
//
// Forge 1.20.1 runs Minecraft with Mojang's class names but scrambled method/field names ("SRG",
// e.g. m_91087_). We write and compile with Mojang's names (Loom provides Minecraft that way), then
// "remapForgeJar" renames everything to SRG names – the mixins' targets too, so no refmap is needed:
//   Mojang name ──(Loom's mappings: named → official)──▶ official name ──(MCPConfig: official → SRG)──▶ SRG
//
// Forge 1.20.1 doesn't include MixinExtras (some mixins use it), so it's packed inside the jar
// ("jar in jar", META-INF/jarjar) and Forge loads it from there.
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
import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val minecraftVersion = "1.20.1"
val forgeVersion = "1.20.1-47.4.10"
val mcpConfigVersion = "1.20.1-20230612.114412"
val mixinExtrasVersion = "0.5.4"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17) // Minecraft 1.20.1 runs on Java 17
}

repositories {
    maven("https://maven.minecraftforge.net/") { name = "Forge" }
}

// The Fabric 1.20.1 build's code and resources, without its Fabric-only parts.
val modern = rootProject.file("mod-fabric-modern")
sourceSets.main {
    java {
        for (folder in listOf("common", "classic", "v1_20", "1.20.1")) {
            srcDir(File(modern, "$folder/src/main/java"))
        }
        exclude("com/nexusclient/modern/NexusModern.java", "com/nexusclient/modern/FabricHooks.java",
                "com/nexusclient/modern/FabricNetworking.java")
    }
    resources {
        srcDir(File(modern, "common/src/main/resources"))
        srcDir(File(modern, "1.20.1/src/main/resources"))
        exclude("fabric.mod.json")
    }
}

// MixinExtras for Forge, packed inside our jar.
val packedInside: Configuration = configurations.create("packedInside") { isTransitive = false }
// MCPConfig: Forge's list of "official → SRG" names for 1.20.1.
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
        "net.minecraftforge:eventbus:6.0.5",
        "net.minecraftforge:forgespi:7.0.1",
        "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7",
        "io.github.llamalad7:mixinextras-common:$mixinExtrasVersion",
    )) {
        compileOnly(artifact) { isTransitive = false }
    }
    packedInside("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    mcpConfig("de.oceanlabs.mcp:mcp_config:$mcpConfigVersion@zip")
    // The shared Nexus code.
    implementation(project(":mod-core"))
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("META-INF/mods.toml") {
        expand("version" to modVersion)
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

// The jar with Mojang's names (input to the renaming below), mod-core and MixinExtras inside.
tasks.jar {
    from(project(":mod-core").extensions.getByType<SourceSetContainer>()["main"].output)
    archiveClassifier.set("mojang-names")
    manifest {
        attributes("MixinConfigs" to "nexusclient.mixins.json")
    }
    from(packedInside) {
        into("META-INF/jarjar")
    }
    from(jarJarMetadata) {
        into("META-INF/jarjar")
    }
}

/** Renames the jar from Mojang's names to Forge's SRG names (Minecraft's own classes keep their names). */
val remapForgeJar = tasks.register("remapForgeJar") {
    dependsOn(tasks.jar)
    val input = tasks.jar.flatMap { it.archiveFile }
    val output = layout.buildDirectory.file("libs/nexus-client-forge-$minecraftVersion-${project.version}.jar")
    inputs.file(input)
    outputs.file(output)
    doLast {
        val loom = LoomGradleExtension.get(project)
        // Loom's mappings: intermediary / named (Mojang) / official.
        val mojang = MemoryMappingTree()
        MappingReader.read(loom.mappingConfiguration.tinyMappings, mojang)
        // MCPConfig's joined.tsrg: obf (= official) → srg.
        val srg = MemoryMappingTree()
        ZipFile(mcpConfig.singleFile).use { zip ->
            zip.getInputStream(zip.getEntry("config/joined.tsrg")).reader().use { reader ->
                MappingReader.read(reader, MappingFormat.TSRG_2_FILE, srg)
            }
        }
        // Both files are keyed by the "official" (scrambled Mojang) names. We walk Forge's list, because
        // Loom's leaves out names Mojang never scrambled (e.g. Axis.rotationDegrees) – Forge still
        // renames those (m_252977_), so for them the Mojang name is simply the official one.
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
                        // Field types aren't in Forge's list; ignoreFieldDesc below matches by name only.
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
        val outFile = output.get().asFile.toPath()
        Files.deleteIfExists(outFile)
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
    dependsOn(remapForgeJar)
}

// Loom's own Fabric remapping isn't used for Forge.
tasks.named("remapJar") { enabled = false }
