# mod-legacy

The Nexus in-game mod for **Minecraft 1.8.9** (the classic PvP version), on **Legacy Fabric**.

It's its own small Gradle build (see `settings.gradle.kts` here for why), but you run it from the
project root like everything else:

- `gradlew :mod-legacy:build` – builds `build/libs/nexus-client-legacy-1.8.9-0.1.0.jar`
- `gradlew :mod-legacy:runClient` – opens Minecraft 1.8.9 with the mod (game folder: `mod-legacy/run`)
- `gradlew :mod-legacy:runClient -PnexusSelfTest` – opens the menus, joins a test world, presses
  Right Shift, takes screenshots (`mod-legacy/run/screenshots`) and closes by itself

The menus, HUD mods and settings come from `mod-core` (shared with every version). This module is
only the "bridge" to 1.8.9: drawing (`LegacyRenderer`), reading the game (`LegacyGameInfo`), opening
screens (`LegacyPlatform`, `NexusScreenWrapper`) and the hooks into the game (`mixin/`).
