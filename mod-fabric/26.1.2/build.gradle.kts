// =====================================================================================
// One older-26.x build of the Nexus mod for Fabric (the version is in gradle.properties next to
// this file). Every 26.x version folder inside mod-fabric has this same file.
//
// 26.x isn't scrambled, so the code is the 26.3 mod's (mod-fabric/src). Minecraft changed a few
// things between 26.1, 26.2 and 26.3: a file in THIS folder's src/ replaces the shared file with the
// same path (e.g. src/main/java/com/nexusclient/fabric/mixin/HudMixin.java). Everything else is shared.
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
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}

val minecraftVersion = property("minecraft_version") as String
val shared = file("../src/main")

// The shared files this version replaces with its own copy (same path under src/main/java).
val replaced = fileTree("src/main/java").files.map { it.relativeTo(file("src/main/java")).invariantSeparatorsPath }

sourceSets.main {
    java {
        srcDir(File(shared, "java"))
        exclude { element -> !element.isDirectory && element.file.startsWith(File(shared, "java")) && element.relativePath.pathString in replaced }
    }
    resources {
        srcDir(File(shared, "resources"))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation(project(":mod-core"))
}

// Test run: gradlew :mod-fabric-<version>:runClient (add -PnexusSelfTest for the self-test).
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

// fabric.mod.json: this version's number and Minecraft version.
tasks.processResources {
    val values = mapOf("version" to project.version.toString(), "minecraft" to minecraftVersion)
    inputs.properties(values)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to values.getValue("version")))
        filter { line -> line.replace("\"~26.3\"", "\"~$minecraftVersion\"") }
    }
}

// Pack mod-core's code inside the mod's .jar, so players only need one file.
tasks.jar {
    from(project(":mod-core").extensions.getByType<SourceSetContainer>()["main"].output)
    archiveBaseName.set("nexus-client-fabric-$minecraftVersion")
}
