# mod-forge

The Nexus in-game mod for **Forge**: Minecraft **26.3** (this folder), **26.2** (`26.2/`),
**26.1.2** (`26.1.2/`), **1.21.11**, **1.21.4**, **1.20.6**, **1.20.4** (`modern/` + a folder each) and
**1.20.1** (`1.20.1/`). None has its own copy of the game code – they all reuse the Fabric code and
only add a Forge start-up class.

## Forge 26.3 (this folder, project `:mod-forge`)

Minecraft 26.x isn't scrambled ("obfuscated"), so Forge runs it with the same class names as
Fabric. This build reuses the Fabric 26.3 mod's code (`mod-fabric`, including all its mixins):

- `NexusForge` – plugs `NexusGame` (in mod-fabric) into Forge's events: keys, HUD layers, the
  crosshair, attacks, joining/leaving servers, the `nexusclient:rules` channel and the game tick.
- `META-INF/mods.toml` – tells Forge what the mod is.
- `pack.mcmeta` – without it Forge ignores the mod's textures, fonts and shaders (and the game hung
  while loading).
- Forge's Mixin (0.8.7) understands Java levels up to 21, so the build marks the mixin classes as
  Java 21 and sets `"compatibilityLevel": "JAVA_21"` in the mixin config. Everything else is Java 25.

## Forge 26.2 and 26.1.2 (`26.2/`, `26.1.2/`, projects `:mod-forge-26.2` and `:mod-forge-26.1.2`)

The same as Forge 26.3, but built from the matching Fabric version: the build takes files from (most
specific first) its own `src/`, then `mod-fabric/<version>/src`, then this folder's `src/`, then
`mod-fabric/src` – a file found earlier wins. Each has its own `pack.mcmeta` (the pack format number
differs per version: 26.2 = 107, 26.1.2 = 101), and the build sets the version range in `mods.toml`.

## Forge 1.20.1 (`1.20.1/`, project `:mod-forge-1.20.1`)

Forge 1.20.1 runs Minecraft with Mojang's class names but scrambled method and field names
("SRG", e.g. `m_91087_`). This build:

1. compiles the Fabric 1.20.1 code (`mod-fabric-modern`: common + classic + v1_20 + 1.20.1, minus
   `NexusModern`, `FabricHooks` and `FabricNetworking`) plus `NexusForge120`, with Mojang's names;
2. renames the finished jar to Forge's names (`remapForgeJar`, using tiny-remapper – the same tool
   Loom uses). The mixin annotations are renamed too, so no "refmap" is needed. The names come from
   Loom's Mojang mappings (Mojang name → original scrambled name) and Forge's MCPConfig
   `joined.tsrg` (original scrambled name → SRG);
3. packs MixinExtras inside the jar (`META-INF/jarjar/`), because Forge 1.20.1 doesn't include it.

Watch out: `@ModifyArgs` doesn't work on Forge (it needs a class Forge's class loader can't make) –
use one `@ModifyArg` per argument instead.

## Forge 1.20.4, 1.20.6, 1.21.4 and 1.21.11 (`modern/` + `1.20.4/`, `1.20.6/`, `1.21.4/`, `1.21.11/`)

Each builds the Fabric code for the same version (`mod-fabric-modern`: common + its shared folders +
the version folder, minus the three Fabric-only files) plus the Forge start-up class
`modern/…/NexusForgeModern`. All four version folders have the same `build.gradle.kts`; what differs
is in their `gradle.properties`:

| Forge | Names in the game | Mixin | Notes |
|---|---|---|---|
| 1.20.4 (49) | SRG, like 1.20.1 | 0.8.5 | the jar is renamed like 1.20.1 (`mcp_config`) |
| 1.20.6 (50) | Mojang's | 0.8.5 | mixins marked Java 18 (`mixin_java`); no HUD event – `ForgeHudMixin` draws the HUD |
| 1.21.4 (54) | Mojang's | 0.8.7 | |
| 1.21.11 (61) | Mojang's | 0.8.7 | EventBus 7 – its own `NexusForgeModern`; includes MixinExtras itself |

1.20.4–1.21.4 don't include MixinExtras, so it's packed inside the jar (`pack_mixinextras`). The only
per-version Forge code is `ForgeHud` (how to draw on the HUD). Each build is limited to the exact
version it was tested on (`minecraft_range`).

## Commands (from the project root)

- `gradlew :mod-forge:build` → `mod-forge/build/libs/nexus-client-forge-26.3-0.1.0.jar`
- `gradlew :mod-forge-26.2:build` / `:mod-forge-26.1.2:build` → `mod-forge/<version>/build/libs/`
- `gradlew :mod-forge-1.20.1:build` → `mod-forge/1.20.1/build/libs/nexus-client-forge-1.20.1-0.1.0.jar`
- `gradlew :mod-forge-1.21.4:forgeJar` (also `-1.20.4`, `-1.20.6`, `-1.21.11`) →
  `mod-forge/<version>/build/libs/nexus-client-forge-<version>-final.jar`

The launcher carries all eight jars and puts the right one into Forge instances
(`ModManager.NEXUS_FORGE_BUILDS`). Forge on 1.20, 1.20.2 and 1.20.3 isn't covered yet (no build tested there; Forge has no 1.20.5).
