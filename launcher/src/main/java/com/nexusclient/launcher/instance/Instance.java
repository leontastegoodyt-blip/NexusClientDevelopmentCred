package com.nexusclient.launcher.instance;

import com.nexusclient.launcher.mods.ModManager;

import java.util.UUID;

/**
 * Instance – one separate Minecraft setup the player can launch, like "PvP" or "SMP"
 * (the project brief calls these "profiles").
 *
 * Each instance has its own folder (see NexusPaths.instanceDir) holding its worlds, mods,
 * resource packs, shaders and game settings, plus the choices saved here.
 *
 * Records can't be changed once made, so the "with..." methods return an updated copy.
 *
 * @param id            a unique code that never changes (also the folder name, so renaming is safe)
 * @param name          what the player called it, e.g. "PvP"
 * @param version       a version name like "1.8.9", LATEST for "always the newest", or null (not picked yet)
 * @param loader        the mod loader: VANILLA (none), FABRIC or FORGE (null in old saves = VANILLA)
 * @param loaderVersion a specific loader version, or null for "the newest stable one"
 * @param ramMb         memory for this instance in MB, or 0 to use the global setting
 * @param jvmArgs       extra Java options for advanced players (empty = none)
 * @param lastPlayed    when it was last launched (milliseconds since 1970), 0 = never
 */
public record Instance(String id, String name, String version, String loader, String loaderVersion,
                       int ramMb, String jvmArgs, long lastPlayed) {

    /** The special version choice that means "always the newest release". */
    public static final String LATEST = "latest";

    public static final String VANILLA = "vanilla";
    public static final String FABRIC = "fabric";
    public static final String FORGE = "forge";

    /** Makes a brand-new instance with no version picked yet (Vanilla – see launchLoader). */
    public static Instance create(String name) {
        return new Instance(UUID.randomUUID().toString(), name, null, VANILLA, null, 0, "", 0);
    }

    /** True if the in-game Nexus mod (Right Shift menu, HUD…) loads in this instance on that version. */
    public boolean getsNexusMod(String gameVersion) {
        return !FORGE.equals(loaderOrVanilla()) && ModManager.hasNexusMod(gameVersion);
    }

    /**
     * The mod loader the game really starts with. Vanilla instances secretly start through Fabric
     * when the Nexus mod exists for that version, so the Nexus mod works without the player
     * switching anything. They get a hidden mods folder with only the Nexus mod in it
     * (ModManager.prepareHiddenNexusMods), so the game still plays like Vanilla.
     */
    public String launchLoader(String gameVersion) {
        if (VANILLA.equals(loaderOrVanilla()) && ModManager.hasNexusMod(gameVersion)) {
            return FABRIC;
        }
        return loaderOrVanilla();
    }

    /** True if this is a Vanilla instance that secretly starts through Fabric (see launchLoader). */
    public boolean secretlyFabric(String gameVersion) {
        return VANILLA.equals(loaderOrVanilla()) && FABRIC.equals(launchLoader(gameVersion));
    }

    /** True once the player has picked a version for this instance. */
    public boolean hasVersion() {
        return version != null;
    }

    /** The mod loader, treating a missing value (from older saves) as Vanilla. */
    public String loaderOrVanilla() {
        return loader == null ? VANILLA : loader;
    }

    /** The mod loader's display name: "Vanilla", "Fabric" or "Forge". */
    public String loaderLabel() {
        return switch (loaderOrVanilla()) {
            case FABRIC -> "Fabric";
            case FORGE -> "Forge";
            default -> "Vanilla";
        };
    }

    public Instance withVersion(String newVersion) {
        return new Instance(id, name, newVersion, loader, loaderVersion, ramMb, jvmArgs, lastPlayed);
    }

    public Instance withName(String newName) {
        return new Instance(id, newName, version, loader, loaderVersion, ramMb, jvmArgs, lastPlayed);
    }

    public Instance withLoader(String newLoader) {
        return new Instance(id, name, version, newLoader, null, ramMb, jvmArgs, lastPlayed);
    }

    public Instance withLoaderVersion(String newLoaderVersion) {
        return new Instance(id, name, version, loader, newLoaderVersion, ramMb, jvmArgs, lastPlayed);
    }

    public Instance withRamMb(int newRamMb) {
        return new Instance(id, name, version, loader, loaderVersion, newRamMb, jvmArgs, lastPlayed);
    }

    public Instance withJvmArgs(String newJvmArgs) {
        return new Instance(id, name, version, loader, loaderVersion, ramMb, newJvmArgs, lastPlayed);
    }

    public Instance withLastPlayed(long time) {
        return new Instance(id, name, version, loader, loaderVersion, ramMb, jvmArgs, time);
    }

    /** A copy with a new unique id (for Duplicate and Import). */
    public Instance withNewId(String newName) {
        return new Instance(UUID.randomUUID().toString(), newName, version, loader, loaderVersion, ramMb, jvmArgs, 0);
    }
}
