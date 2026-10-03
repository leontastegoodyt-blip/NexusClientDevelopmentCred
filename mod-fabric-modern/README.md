# mod-fabric-modern

The Nexus in-game mod for **Fabric on Minecraft 1.20 – 1.21.11** (26.3 is in `mod-fabric`, 1.8.9 in
`mod-legacy`). The menus, HUD mods and settings come from `mod-core`; this folder is only the
"bridge" between them and the game.

Minecraft's code changes a little between versions, so there is one build per group of versions
whose code is the same:

| Build folder | Plays on            | Java |
|--------------|---------------------|------|
| `1.20.1`     | 1.20, 1.20.1        | 17   |
| `1.20.4`     | 1.20.2 – 1.20.4     | 17   |
| `1.20.6`     | 1.20.5, 1.20.6      | 21   |
| `1.21.4`     | 1.21.4              | 21   |
| `1.21.11`    | 1.21.11             | 21   |

The code is split so each piece is written only once:

- `common/` – the same on every version (start-up, tick, HUD drawing, game info, chat mods, most hooks)
- `classic/` – shared by 1.20.1, 1.20.4, 1.20.6 and 1.21.4: keys, mouse and HUD the "old" way
  (`GameHooks`, `NexusScreenWrapper`, click counting). 1.21.11 has its own copies of these files,
  because Minecraft changed them in 1.21.6 – 1.21.9.
- `<version>/` – only what's different in that version: `VersionHooks` (item/effect/biome look-ups,
  the server-rules channel, the self-test world), the zoom and longer-chat hooks, `nexusclient.mixins.json`.

Each version folder's `gradle.properties` says which Minecraft/Fabric API/Java versions it uses
and which shared folders it includes (`shared_folders=classic`).

Commands (from the project root):

- `gradlew :mod-fabric-1.20.1:build` – builds `1.20.1/build/libs/nexus-client-fabric-1.20.1-0.1.0.jar`
- `gradlew :mod-fabric-1.20.1:runClient` – opens that Minecraft with the mod (game folder: `1.20.1/run`)
- `gradlew :mod-fabric-1.20.1:runClient -PnexusSelfTest` – opens the menus, joins a test world,
  presses Right Shift, takes screenshots (`run/screenshots`) and closes by itself

To support another version: copy the closest version folder, change its `gradle.properties`, add it
to the list in the root `settings.gradle.kts` and `launcher/build.gradle.kts`, add it to
`NEXUS_MOD_BUILDS` in the launcher's `ModManager.java`, then fix whatever doesn't compile.
