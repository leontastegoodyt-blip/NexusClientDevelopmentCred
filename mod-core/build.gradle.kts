// =====================================================================================
// mod-core – shared in-game mod code: the list of mods and their settings, the HUD layout,
// saving/loading, share codes, and the mod menu + HUD editor screens.
//
// None of this code talks to Minecraft directly. Instead it draws through a small "Renderer"
// and reads the game through "GameInfo" – each loader module (mod-fabric, mod-forge,
// mod-legacy) provides those. So every feature is written once and works everywhere.
//
// Minecraft 1.8.9 runs on Java 8, so this code must stay Java 8 compatible:
// no records, no "var", no switch expressions, no text blocks.
// The shared settings (Java toolchain etc.) come from the root build.gradle.kts.
// =====================================================================================

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8) // produce Java 8 class files (works on Java 8 and every newer Java)
    options.compilerArgs.add("-Xlint:-options") // (hide the "Java 8 is old" warning – we need it for 1.8.9)
}

dependencies {
    // Gson reads/writes the settings files. Minecraft already includes Gson in every version
    // (1.8.9 has an old 2.2.4), so it's "compileOnly": used to build, not packed into the mod.
    // Only features that exist in Gson 2.2.4 may be used.
    compileOnly("com.google.code.gson:gson:2.2.4")
}
