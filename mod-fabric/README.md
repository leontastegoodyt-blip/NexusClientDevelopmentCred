# mod-fabric

The Nexus in-game mod for **Fabric** on Minecraft **26.3** (this folder, project `:mod-fabric`),
**26.2** (`26.2/`, project `:mod-fabric-26.2`) and **26.1.2** (`26.1.2/`, project `:mod-fabric-26.1.2`).
It connects the shared code in `mod-core` to Minecraft through Fabric. (1.20–1.21.11 live in
`mod-fabric-modern/`, 1.8.9 in `mod-legacy/`.)

## How the older 26.x builds work

26.1.2 and 26.2 are close to 26.3, so they don't have their own copy of the mod. Their build
compiles this folder's `src/` and then **swaps in** any file with the same path from their own
`src/` folder (the build file leaves the 26.3 one out). Only what really changed is swapped:

- `Compat.java` – the small game calls that changed (where the open screen and chat live, fullscreen,
  item rotation, team scoreboard slots, screenshots). The rest of the code calls `Compat` instead
  of the game directly, so it stays the same everywhere.
- `DfrRenderer.java` + `mixin/DfrMixin.java` – DFR. 26.2 has 26.3's graphics layer under its old
  package names (`com.mojang.blaze3d`); 26.1.2 still uses plain OpenGL (like 1.21.11).
- A few mixins whose target method was renamed or moved (time changer clock, small items, motion
  blur, and on 26.1.2 the block outline, low fire and hidden vanilla HUD).

To add something new: write it once in `src/`. If it doesn't compile for 26.1.2 or 26.2, add a
method to `Compat` (all three versions) instead of copying whole files.

## Commands (from the project root)

- `gradlew :mod-fabric:runClient -PnexusSelfTest` (also `:mod-fabric-26.2:` and `:mod-fabric-26.1.2:`)
  opens the game, tests the mod menu, HUD editor, a test world and DFR, saves screenshots to
  `run/screenshots`, then closes.
- `gradlew :mod-fabric:build` → `build/libs/`

The Forge builds for the same versions (`mod-forge/`) reuse this code too.
