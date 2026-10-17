# Nexus Client

A Minecraft client (launcher + in-game mod) built to compete with clients like Lunar and Feather.
Dark, neon look; signature feature: **DFR (Dual Frame Reset)** – generated in-between frames for
smoother gameplay.

> Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

## What's in here

| Folder | What it is |
|---|---|
| `launcher/` | The desktop app players open (Java + JavaFX): instances, Microsoft sign-in, Modrinth mods/packs/shaders, TorcherRig hardware optimiser, Game Error Scan, tools, friends, clans |
| `mod-core/` | The in-game mod's shared code: Right Shift mod menu, HUD editor, HUD/PvP/visual/chat/utility mods, settings, DFR logic. Written once, used by every Minecraft version |
| `mod-fabric/` | The in-game mod for Fabric on Minecraft **26.3, 26.2 and 26.1.2** (see its README) |
| `mod-fabric-modern/` | The in-game mod for Fabric on Minecraft **1.20 – 1.20.6, 1.21.4 and 1.21.11** (see its README) |
| `mod-legacy/` | The in-game mod for Minecraft **1.8.9** on Legacy Fabric (its own small Gradle build) |
| `mod-forge/` | The in-game mod for Forge on Minecraft **26.3, 26.2, 26.1.2, 1.21.11, 1.21.4, 1.20.6, 1.20.4 and 1.20.1** (reuses the Fabric code; see its README) |
| `backend/` | The online server (Spring Boot): accounts, friends, DMs, parties, clans, Partner Servers, cosmetics |
| `docs/` | Release checklist, the guide for server owners (`server-rules.md`), draft privacy policy and terms |

The launcher carries the right in-game mod for every supported version and adds it automatically.
Vanilla instances quietly start through Fabric with only the Nexus mod loaded, so the Right Shift
menu and HUD always work.

## Building and running

You need **JDK 21** (e.g. `winget install EclipseAdoptium.Temurin.21.JDK`). Everything else –
Gradle, the other Java versions, Minecraft itself – is downloaded automatically. Use `gradlew.bat`
on Windows and `./gradlew` on Mac/Linux.

| Command | What it does |
|---|---|
| `gradlew :launcher:run` | Opens the launcher (developer mode: an offline test player if nobody is signed in) |
| `gradlew assemble` | Builds everything |
| `gradlew :mod-fabric:runClient` | Opens Minecraft 26.3 with the Nexus mod (also `:mod-fabric-26.2:`, `:mod-fabric-26.1.2:`) |
| `gradlew :mod-fabric-1.20.1:runClient` | Same for 1.20.1 (also `-1.20.4`, `-1.20.6`, `-1.21.4`, `-1.21.11`) |
| `gradlew :mod-legacy:runClient` | Same for 1.8.9 |
| `gradlew :backend:bootRun` | Starts the online server at http://localhost:8080 |
| `gradlew :backend:test` | Runs the backend's tests |
| `gradlew :launcher:jpackage` | Builds an installer for this computer's system |

Add `-PnexusSelfTest` to any `runClient` command for an automatic check: it opens the mod menu and
HUD editor, joins a test world, presses Right Shift and saves screenshots to that module's
`run/screenshots` folder, then closes the game.

## Supported Minecraft versions (in-game mod)

| Version | Mod loader | Status |
|---|---|---|
| 26.3 (latest), 26.2, 26.1.2 | Fabric and Forge | ✅ all built mods + DFR + cosmetics |
| 1.21.11, 1.21.4 | Fabric | ✅ |
| 1.20 – 1.20.6 | Fabric | ✅ |
| 1.8.9 | Legacy Fabric | ✅ (1.7 animations coming) |
| 1.21.11, 1.21.4, 1.20.6, 1.20.4, 1.20.1 | Forge | ✅ |
| 1.20, 1.20.2, 1.20.3 | Forge | ⏳ the game runs, without the Nexus mod |

See [`docs/release-checklist.md`](docs/release-checklist.md) for what's left before a public release.
