# mod-forge

The Nexus in-game mod for **Forge on Minecraft 26.3**.

Minecraft 26.x isn't scrambled ("obfuscated"), so Forge runs it with the same class names as
Fabric. This module therefore reuses the Fabric 26.3 mod's code (`mod-fabric`, including all its
mixins) and only adds its own start-up class:

- `NexusForge` – plugs `NexusGame` (in mod-fabric) into Forge's events: keys, HUD layers, the
  crosshair, attacks, joining/leaving servers, the `nexusclient:rules` channel and the game tick.
- `META-INF/mods.toml` – tells Forge what the mod is.
- `pack.mcmeta` – without it Forge ignores the mod's textures, fonts and shaders.

Things to know:

- No Forge build tool is needed. Loom only provides Minecraft's classes to compile against; Forge's
  own classes come from Forge's Maven server as compile-only dependencies.
- Forge's Mixin (0.8.7) understands Java levels up to 21, so the build marks the mixin classes as
  Java 21 and sets `"compatibilityLevel": "JAVA_21"` in the mixin config. Everything else is Java 25.
- Forge reads the mixin config from the jar's manifest (`MixinConfigs`).

Commands (from the project root):

- `gradlew :mod-forge:build` – builds `build/libs/nexus-client-forge-26.3-0.1.0.jar`

The launcher carries this jar and puts it into Forge 26.3 instances (`ModManager.NEXUS_FORGE_BUILDS`).
Forge on 1.20.x would need a real Forge build (those versions are scrambled), so it isn't done yet.
