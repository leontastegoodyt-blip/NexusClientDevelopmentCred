// =====================================================================================
// launcher – the Nexus desktop app that players open.
//
// This file tells Gradle three things about the launcher:
//   1. it is an "application" (a program you can run, not just a library)
//   2. it uses JavaFX to draw its windows
//   3. which Java class to start when you run it
// The shared settings (Java 21 etc.) come from the root build.gradle.kts.
// =====================================================================================

plugins {
    // Adds the "run" command, so `gradlew :launcher:run` starts the app.
    application

    // Downloads JavaFX (the window/UI toolkit) and sets it up for us.
    // No separate JavaFX install needed.
    id("org.openjfx.javafxplugin") version "0.1.0"

    // Builds the installers (release checklist): a small Java runtime made just for Nexus (jlink),
    // packed with the app by jpackage – .exe/.msi on Windows, .dmg on Mac, .deb on Linux.
    id("org.beryx.runtime") version "2.0.1"
}

javafx {
    // JavaFX 21 is the long-term-support version that matches Java 21.
    version = "21.0.12"
    // The parts of JavaFX we use. "controls" gives us buttons, labels, layouts etc.
    modules = listOf("javafx.controls")
}

dependencies {
    // Gson reads JSON – the text format Mojang uses for its version lists and game file lists.
    implementation("com.google.code.gson:gson:2.14.0")
    // JNA lets Java use Windows' built-in encryption (DPAPI) to store login secrets safely.
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    // OSHI reads the PC's hardware (CPU, graphics card, RAM) for TorcherRig.
    implementation("com.github.oshi:oshi-core:6.12.0")
    // OSHI writes log messages through "SLF4J"; this add-on quietly throws them away.
    implementation("org.slf4j:slf4j-nop:2.0.17")
    // Reads WebP pictures (most Modrinth mod icons are WebP, which JavaFX can't open by itself).
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
}

// Pack the in-game Nexus mod (built by mod-fabric) inside the launcher, so the launcher can put it
// into players' Fabric instances. Building the launcher builds the mod first automatically.
// The file names must match NEXUS_MOD_BUILDS in mods/ModManager.java.
tasks.processResources {
    from(project(":mod-fabric").tasks.named("jar")) {
        into("com/nexusclient/launcher/nexus-mod")
        rename { "nexus-client-fabric-26.3.jar" }
    }
    // The 1.20–1.21.11 builds (mod-fabric-modern); "remapJar" makes the file the real game can load.
    for (version in listOf("1.20.1", "1.20.4", "1.20.6", "1.21.4", "1.21.11")) {
        from(project(":mod-fabric-$version").tasks.named("remapJar")) {
            into("com/nexusclient/launcher/nexus-mod")
            rename { "nexus-client-fabric-$version.jar" }
        }
    }
    // The 1.8.9 mod is its own build (mod-legacy), so it's built first and its file copied in.
    dependsOn(gradle.includedBuild("mod-legacy").task(":remapJar"))
    from(rootProject.file("mod-legacy/build/libs/nexus-client-legacy-1.8.9-0.1.0.jar")) {
        into("com/nexusclient/launcher/nexus-mod")
        rename { "nexus-client-legacy-1.8.9.jar" }
    }
}

application {
    // The class whose main() method starts the launcher.
    mainClass.set("com.nexusclient.launcher.Main") // Main starts NexusLauncher (see Main.java for why)
}

// `gradlew :launcher:run` is how we run Nexus while building it, so turn on "developer mode" there.
// Developer mode allows the temporary test player when nobody is signed in. Real releases never have it.
tasks.named<JavaExec>("run") {
    systemProperty("nexus.dev", "true")
    // Developer tools (optional):
    //   gradlew :launcher:run -PnexusTour=C:\folder   → photographs every screen into that folder, then closes
    //   gradlew :launcher:run -PnexusFirstRun=true    → shows the first-run welcome screens again
    providers.gradleProperty("nexusTour").orNull?.let { systemProperty("nexus.tour", it) }
    //   …add -PnexusTourSize=1024x600 to photograph the screens at that window size
    providers.gradleProperty("nexusTourSize").orNull?.let { systemProperty("nexus.tourSize", it) }
    //   …add -PnexusTourScreen=true to capture the real pixels on screen (checks sharpness exactly)
    providers.gradleProperty("nexusTourScreen").orNull?.let { systemProperty("nexus.tourScreen", it) }
    providers.gradleProperty("nexusFirstRun").orNull?.let { systemProperty("nexus.firstRun", it) }
    //   gradlew :launcher:run -PnexusHome=C:\folder   → uses that folder instead of the real Nexus folder
    providers.gradleProperty("nexusHome").orNull?.let { systemProperty("nexus.home", it) }
}

// =====================================================================================
// Installers.
//   gradlew :launcher:jpackageImage   → build/jpackage/Nexus Client/  (a ready-to-run app folder)
//   gradlew :launcher:jpackage        → the installer for this computer's system
// Windows installers (.exe/.msi) need the free WiX Toolset installed first
// (winget install WiXToolset.WiXToolset). Mac and Windows builds must be code-signed before release.
// =====================================================================================
runtime {
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages"))
    // The parts of Java that Nexus uses (keeps the bundled Java small).
    modules.set(listOf(
            "java.base", "java.desktop", "java.logging", "java.net.http", "java.naming", "java.sql",
            "java.xml", "java.scripting", "java.management", "jdk.management", "jdk.httpserver",
            "jdk.unsupported", "jdk.crypto.ec", "jdk.naming.dns", "jdk.charsets", "jdk.localedata"))
    jpackage {
        imageName = "Nexus Client"
        installerName = "Nexus Client"
        appVersion = project.version.toString()
        val os = System.getProperty("os.name").lowercase()
        installerType = when {
            os.contains("win") -> "exe"
            os.contains("mac") -> "dmg"
            else -> "deb"
        }
        if (os.contains("win")) {
            installerOptions = listOf("--win-menu", "--win-shortcut", "--win-dir-chooser",
                    "--win-menu-group", "Nexus Client", "--vendor", "Nexus Client")
        }
        jvmArgs = listOf("-Dfile.encoding=UTF-8")
    }
}
