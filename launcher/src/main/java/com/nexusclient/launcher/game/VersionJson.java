package com.nexusclient.launcher.game;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * VersionJson – everything Mojang's JSON file for one Minecraft version says: which files the game
 * needs, which Java it needs, and how to start it.
 *
 * Each record below matches one part of the JSON, so Gson can fill them in automatically.
 * (Fields the JSON doesn't have are left as null.)
 *
 * @param id                 the version name, e.g. "1.8.9"
 * @param type               "release" or "snapshot"
 * @param mainClass          the Java class that starts the game
 * @param minecraftArguments OLD versions (like 1.8.9): the game's start-up options as one line of text
 * @param arguments          NEW versions: start-up options for the game and for Java, as lists
 * @param assetIndex         where to find the list of sounds/textures
 * @param downloads          the game itself ("client") and the server
 * @param libraries          the code packages the game needs
 * @param javaVersion        which Java this version needs
 * @param logging            the game's logging settings file (this is what fixes "Log4Shell")
 */
public record VersionJson(
        String id,
        String type,
        String mainClass,
        String minecraftArguments,
        Arguments arguments,
        AssetIndexRef assetIndex,
        Map<String, Artifact> downloads,
        List<Library> libraries,
        JavaVersion javaVersion,
        Logging logging) {

    /** Start-up options. Each item is either plain text or a {rules, value} object (see Rules). */
    public record Arguments(List<JsonElement> game, List<JsonElement> jvm) {}

    /** Where the sounds/textures list is. "id" is its name, e.g. "1.8". */
    public record AssetIndexRef(String id, String sha1, long size, String url) {}

    /** One downloadable file. "path" is where it goes inside the libraries folder. */
    public record Artifact(String path, String sha1, long size, String url) {}

    /**
     * One code package, e.g. "org.lwjgl:lwjgl:3.3.3".
     *
     * @param natives OLD versions: which "classifier" download holds the computer-specific files
     *                for each operating system
     * @param extract OLD versions: what NOT to unpack from the natives file
     * @param rules   when this library is needed, e.g. "only on Mac"
     * @param url     MOD LOADERS (Fabric): the library server to download it from (its path inside
     *                the server comes from the name – see ModLoaders.mavenPath)
     * @param sha1    MOD LOADERS: the file's fingerprint (null if not given)
     * @param size    MOD LOADERS: the file's size in bytes (0 if not given)
     */
    public record Library(String name, LibraryDownloads downloads, Map<String, String> natives,
                          Extract extract, List<Rule> rules, String url, String sha1, long size) {}

    public record LibraryDownloads(Artifact artifact, Map<String, Artifact> classifiers) {}

    public record Extract(List<String> exclude) {}

    /**
     * A rule: "allow" or "disallow", if the computer matches "os" and the launcher's "features".
     * See Rules.java for how they're checked.
     */
    public record Rule(String action, OsRule os, Map<String, Boolean> features) {}

    public record OsRule(String name, String arch, String version) {}

    /** e.g. component "jre-legacy", majorVersion 8. */
    public record JavaVersion(String component, int majorVersion) {}

    public record Logging(LoggingConfig client) {}

    /** "argument" is the Java option to add, with ${path} replaced by where we saved "file". */
    public record LoggingConfig(String argument, LoggingFile file) {}

    public record LoggingFile(String id, String sha1, long size, String url) {}
}
