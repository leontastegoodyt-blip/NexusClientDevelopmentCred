package com.nexusclient.launcher.game;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ModLoaders – installs mod loaders: small programs that load mods into Minecraft.
 *
 * Fabric works like this: Fabric's server gives us a version JSON (a "profile") that says
 * "start from Minecraft 1.20.1, but add these extra libraries and start with Fabric's main class".
 * We download it and combine it with the normal Minecraft version JSON (see merge()).
 *
 * Minecraft 1.8.9 isn't supported by normal Fabric, so it uses "Legacy Fabric", a community version
 * with the same kind of server. Forge needs its own installer program to run – see forgeProfile().
 */
public final class ModLoaders {

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/";
    private static final String LEGACY_FABRIC_META = "https://meta.legacyfabric.net/v2/versions/loader/";

    private static final Gson GSON = new Gson();

    private ModLoaders() {}

    /**
     * Gets the Fabric profile for a Minecraft version (downloading it the first time).
     *
     * @param loaderVersion a specific Fabric loader version, or null for the newest stable one
     */
    public static VersionJson fabricProfile(String gameVersion, String loaderVersion, Downloader downloader,
                                            ProgressListener progress) throws IOException, InterruptedException {
        progress.update("Getting the Fabric mod loader…", -1);
        String meta = usesLegacyFabric(gameVersion) ? LEGACY_FABRIC_META : FABRIC_META;
        try {
            String chosen = loaderVersion != null ? loaderVersion : newestStableLoader(meta, gameVersion, downloader);
            String json = downloader.fetchString(meta + gameVersion + "/" + chosen + "/profile/json");
            VersionJson profile = GSON.fromJson(json, VersionJson.class);
            // Keep a copy so the instance can still start without internet next time.
            Path file = NexusPaths.versions().resolve(profile.id()).resolve(profile.id() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, json);
            return profile;
        } catch (IOException e) {
            VersionJson cached = cachedProfile(gameVersion);
            if (cached != null) {
                return cached; // offline: use the one saved last time
            }
            throw new IOException("Couldn't download the Fabric mod loader. Check your internet connection.", e);
        }
    }

    // ================================================================== Forge

    private static final String FORGE_PROMOTIONS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/";

    /**
     * Gets the Forge profile for a Minecraft version, installing Forge the first time.
     *
     * Unlike Fabric, Forge has to change ("patch") Minecraft's own game file, which only Forge's
     * installer program can do. So the first time, Nexus downloads Forge's official installer and runs
     * it quietly ("--installClient") into the Nexus folder, which is laid out like Minecraft's own
     * (versions/, libraries/). The installer writes versions/<id>/<id>.json – a profile just like
     * Fabric's – and the patched game files. After that, Forge starts like any other version.
     *
     * @param loaderVersion a specific Forge version (e.g. "47.4.10"), or null for the recommended one
     * @param javaExe       the Java to run the installer with (the game's own)
     */
    public static VersionJson forgeProfile(String gameVersion, String loaderVersion, Path javaExe, Downloader downloader,
                                           ProgressListener progress) throws IOException, InterruptedException {
        if (usesLegacyFabric(gameVersion)) {
            throw new IOException("Nexus runs Forge on Minecraft 1.20 and newer. For " + gameVersion
                    + ", switch this instance to Fabric in its settings.");
        }
        progress.update("Getting the Forge mod loader…", -1);
        String forge;
        try {
            forge = loaderVersion != null ? loaderVersion : recommendedForge(gameVersion, downloader);
        } catch (IOException e) {
            VersionJson cached = cachedForge(gameVersion, null);
            if (cached != null) {
                return cached; // offline: use the Forge installed last time
            }
            throw new IOException("Couldn't reach Forge's website. Check your internet connection.", e);
        }
        VersionJson installed = cachedForge(gameVersion, forge);
        if (installed != null) {
            return installed;
        }

        // Download Forge's installer…
        String full = gameVersion + "-" + forge;
        Path installer = NexusPaths.root().resolve("installers").resolve("forge-" + full + "-installer.jar");
        downloader.download(new Downloader.Job(FORGE_MAVEN + full + "/forge-" + full + "-installer.jar", installer, null, 0));
        // …which only installs into a folder that looks like the official launcher's.
        Path profiles = NexusPaths.root().resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            Files.writeString(profiles, "{\"profiles\":{}}");
        }

        progress.update("Installing Forge " + forge + " (first time only, about a minute)…", -1);
        Path log = NexusPaths.root().resolve("logs").resolve("forge-install.log");
        Files.createDirectories(log.getParent());
        Process process = new ProcessBuilder(javaExe.toString(), "-jar", installer.toString(),
                "--installClient", NexusPaths.root().toAbsolutePath().toString())
                .directory(installer.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(15, java.util.concurrent.TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Installing Forge took too long. Try again – the log is in " + log);
        }
        installed = cachedForge(gameVersion, forge);
        if (process.exitValue() != 0 || installed == null) {
            throw new IOException("Forge's installer didn't finish (" + lastLine(log) + "). The full log is in " + log);
        }
        return installed;
    }

    /** Forge's recommended version for this Minecraft version (or its newest, if none is recommended yet). */
    private static String recommendedForge(String gameVersion, Downloader downloader) throws IOException, InterruptedException {
        JsonObject promos = JsonParser.parseString(downloader.fetchString(FORGE_PROMOTIONS))
                .getAsJsonObject().getAsJsonObject("promos");
        for (String kind : new String[] {"-recommended", "-latest"}) {
            if (promos.has(gameVersion + kind)) {
                return promos.get(gameVersion + kind).getAsString();
            }
        }
        throw new IOException("Forge isn't available for Minecraft " + gameVersion + " yet.");
    }

    /**
     * The installed Forge profile for this Minecraft version (a specific Forge version, or the newest
     * one installed if forge is null), or null. Installed means the profile and Forge's patched game
     * file are both there.
     */
    private static VersionJson cachedForge(String gameVersion, String forge) throws IOException {
        if (!Files.isDirectory(NexusPaths.versions())) {
            return null;
        }
        String prefix = gameVersion + "-forge-";
        try (Stream<Path> dirs = Files.list(NexusPaths.versions())) {
            List<Path> matches = dirs.filter(dir -> forge != null
                            ? dir.getFileName().toString().equals(prefix + forge)
                            : dir.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .toList();
            for (int i = matches.size() - 1; i >= 0; i--) {
                Path dir = matches.get(i);
                Path json = dir.resolve(dir.getFileName() + ".json");
                String version = dir.getFileName().toString().substring(prefix.length());
                String full = gameVersion + "-" + version;
                Path patched = NexusPaths.libraries().resolve("net/minecraftforge/forge/" + full + "/forge-" + full + "-client.jar");
                if (Files.exists(json) && Files.exists(patched)) {
                    return GSON.fromJson(Files.readString(json), VersionJson.class);
                }
            }
        }
        return null;
    }

    private static String lastLine(Path log) {
        try {
            List<String> lines = Files.readAllLines(log);
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (!lines.get(i).isBlank()) {
                    return lines.get(i).trim();
                }
            }
        } catch (IOException e) {
            // no log
        }
        return "no details";
    }

    /** Legacy Fabric covers old versions (before 1.14) that normal Fabric doesn't support. */
    private static boolean usesLegacyFabric(String gameVersion) {
        if (!gameVersion.startsWith("1.")) {
            return false;
        }
        try {
            int minor = Integer.parseInt(gameVersion.split("\\.")[1]);
            return minor < 14;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The newest loader version marked "stable" for this Minecraft version. */
    private static String newestStableLoader(String meta, String gameVersion, Downloader downloader)
            throws IOException, InterruptedException {
        JsonArray loaders = JsonParser.parseString(downloader.fetchString(meta + gameVersion)).getAsJsonArray();
        if (loaders.isEmpty()) {
            throw new IOException("Fabric doesn't support Minecraft " + gameVersion + " yet.");
        }
        for (JsonElement entry : loaders) { // newest first
            JsonObject loader = entry.getAsJsonObject().getAsJsonObject("loader");
            if (loader.has("stable") && loader.get("stable").getAsBoolean()) {
                return loader.get("version").getAsString();
            }
        }
        return loaders.get(0).getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
    }

    /** The newest Fabric profile saved for this Minecraft version, or null. */
    private static VersionJson cachedProfile(String gameVersion) throws IOException {
        if (!Files.isDirectory(NexusPaths.versions())) {
            return null;
        }
        try (Stream<Path> dirs = Files.list(NexusPaths.versions())) {
            Path newest = dirs.filter(dir -> dir.getFileName().toString().startsWith("fabric-loader-")
                            && dir.getFileName().toString().endsWith("-" + gameVersion))
                    .max(Path::compareTo)
                    .orElse(null);
            if (newest == null) {
                return null;
            }
            Path json = newest.resolve(newest.getFileName() + ".json");
            return Files.exists(json) ? GSON.fromJson(Files.readString(json), VersionJson.class) : null;
        }
    }

    /**
     * Combines a loader profile ("child") with the Minecraft version it builds on ("parent"):
     *   - the child's main class and id win
     *   - libraries: the child's first; a parent library is skipped if the child has its own copy
     *   - start-up options: the parent's, then the child's extra ones
     *   - everything else (game file, assets, Java version, logging) comes from the parent
     */
    public static VersionJson merge(VersionJson parent, VersionJson child) {
        List<VersionJson.Library> libraries = new ArrayList<>(child.libraries());
        Set<String> childKeys = new LinkedHashSet<>();
        for (VersionJson.Library library : child.libraries()) {
            childKeys.add(libraryKey(library.name()));
        }
        for (VersionJson.Library library : parent.libraries()) {
            if (!childKeys.contains(libraryKey(library.name()))) {
                libraries.add(library);
            }
        }

        VersionJson.Arguments arguments = parent.arguments();
        if (child.arguments() != null) {
            arguments = new VersionJson.Arguments(
                    join(parent.arguments() == null ? null : parent.arguments().game(), child.arguments().game()),
                    join(parent.arguments() == null ? null : parent.arguments().jvm(), child.arguments().jvm()));
        }

        return new VersionJson(child.id(), parent.type(),
                child.mainClass() != null ? child.mainClass() : parent.mainClass(),
                child.minecraftArguments() != null ? child.minecraftArguments() : parent.minecraftArguments(),
                arguments, parent.assetIndex(), parent.downloads(), libraries, parent.javaVersion(), parent.logging());
    }

    private static List<JsonElement> join(List<JsonElement> first, List<JsonElement> second) {
        List<JsonElement> joined = new ArrayList<>();
        if (first != null) joined.addAll(first);
        if (second != null) joined.addAll(second);
        return joined;
    }

    /**
     * "group:name:version[:classifier]" → "group:name[:classifier]", so two versions of the same
     * library count as the same library.
     */
    private static String libraryKey(String name) {
        String[] parts = name.split(":");
        if (parts.length >= 4) {
            return parts[0] + ":" + parts[1] + ":" + parts[3];
        }
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : name;
    }

    /**
     * Where a library lives inside a "Maven" library server, worked out from its name:
     * "org.ow2.asm:asm:9.7" → "org/ow2/asm/asm/9.7/asm-9.7.jar"
     */
    public static String mavenPath(String name) {
        String extension = "jar";
        int at = name.indexOf('@');
        if (at >= 0) {
            extension = name.substring(at + 1);
            name = name.substring(0, at);
        }
        String[] parts = name.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + "." + extension;
    }
}
