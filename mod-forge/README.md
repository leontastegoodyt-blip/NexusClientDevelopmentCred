# mod-forge

The Nexus in-game mod for **Forge**: Minecraft **26.3** (this folder) and **1.20.1** (`1.20.1/`).
Neither has its own copy of the game code – both reuse the Fabric code and only add a Forge
start-up class.

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

## Commands (from the project root)

- `gradlew :mod-forge:build` → `mod-forge/build/libs/nexus-client-forge-26.3-0.1.0.jar`
- `gradlew :mod-forge-1.20.1:build` → `mod-forge/1.20.1/build/libs/nexus-client-forge-1.20.1-0.1.0.jar`

The launcher carries both jars and puts the right one into Forge instances
(`ModManager.NEXUS_FORGE_BUILDS`). Other Forge versions (1.20.2–1.21.x) would work the same way as
1.20.1 (their own MCPConfig), but aren't set up yet.
