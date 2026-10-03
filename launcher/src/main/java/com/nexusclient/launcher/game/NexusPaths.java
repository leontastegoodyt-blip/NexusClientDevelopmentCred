package com.nexusclient.launcher.game;

import com.nexusclient.launcher.instance.Instance;

import java.nio.file.Path;

/**
 * NexusPaths – where Nexus keeps its files on the computer.
 *
 *   Windows: C:\Users\<you>\AppData\Roaming\NexusClient
 *   Mac:     ~/Library/Application Support/NexusClient
 *   Linux:   ~/.local/share/nexusclient
 *
 * Inside that folder:
 *   versions/   the Minecraft game itself, one folder per version
 *   libraries/  code packages Minecraft needs (shared between versions)
 *   assets/     sounds, languages and textures (shared between versions)
 *   runtimes/   the Java versions Nexus downloaded
 *   natives/    computer-specific files (.dll/.dylib/.so) unpacked for each version
 *   instances/  one game folder per instance: its worlds, settings, screenshots, logs
 */
public final class NexusPaths {

    private NexusPaths() {}

    /** The main Nexus folder. */
    public static Path root() {
        // Developer option: -Dnexus.home=<folder> uses a different folder (handy for testing safely).
        String override = System.getProperty("nexus.home");
        if (override != null) {
            return Path.of(override);
        }
        String home = System.getProperty("user.home");
        return switch (OperatingSystem.current()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield Path.of(appData != null ? appData : home, "NexusClient");
            }
            case MAC -> Path.of(home, "Library", "Application Support", "NexusClient");
            case LINUX -> {
                String dataHome = System.getenv("XDG_DATA_HOME");
                yield Path.of(dataHome != null ? dataHome : home + "/.local/share", "nexusclient");
            }
        };
    }

    public static Path versions()  { return root().resolve("versions"); }
    public static Path libraries() { return root().resolve("libraries"); }
    public static Path assets()    { return root().resolve("assets"); }
    public static Path runtimes()  { return root().resolve("runtimes"); }
    public static Path instances() { return root().resolve("instances"); }
    /** Extra programs Nexus downloads when needed (e.g. FFmpeg for the in-game clip recorder). */
    public static Path tools()     { return root().resolve("tools"); }

    /** Where a version's computer-specific files get unpacked. */
    public static Path natives(String versionId) {
        return root().resolve("natives").resolve(versionId);
    }

    /**
     * An instance's own game folder (worlds, mods, options, screenshots, logs).
     * It's named after the instance's unique id, so renaming an instance never breaks it.
     */
    public static Path instanceDir(Instance instance) {
        return instances().resolve(instance.id());
    }

    /**
     * Turns a name into something safe to use as a folder name, by replacing characters
     * that Windows/Mac/Linux don't allow in file names (like \ / : * ? " < > |) with "_".
     */
    public static String safeFileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return safe.isEmpty() ? "instance" : safe;
    }
}
