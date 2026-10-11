// =====================================================================================
// A tiny Gradle setup that builds ONLY the Nexus online server (backend/).
//
// The Dockerfile next to this file uses it instead of the project's real settings.gradle.kts,
// because the real one also includes the Minecraft mods – building those downloads Minecraft
// itself, which is slow and too big for Render's free builder.
// =====================================================================================
rootProject.name = "nexus-server"

include("backend")
