package com.nexusclient.launcher.minecraft;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * VersionManifest – downloads Mojang's official list of every Minecraft version and picks out
 * the ones Nexus supports.
 *
 * Mojang publishes this list (called the "version manifest") as a JSON file on their servers.
 * The official Minecraft launcher reads the same file.
 */
public final class VersionManifest {

    /** Where Mojang keeps the list of all Minecraft versions. */
    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    /** The one old version Nexus supports (the classic PvP version). */
    private static final String LEGACY_VERSION = "1.8.9";

    /** Extra popular versions the owner asked for, besides the newest, every 1.20.x, and 1.8.9. */
    private static final List<String> EXTRA_VERSIONS = List.of("26.2", "26.1.2", "1.21.11", "1.21.4");

    // These two records describe the shape of Mojang's JSON so Gson can read it.
    // The JSON looks like: { "latest": { "release": "26.3", ... }, "versions": [ ... ] }
    // "private" means only this file uses them.
    private record ManifestJson(Latest latest, List<MinecraftVersion> versions) {}
    private record Latest(String release, String snapshot) {}

    // This class only has static methods, so nobody needs to create one.
    private VersionManifest() {}

    /**
     * Downloads the version list from Mojang and returns the versions Nexus supports:
     * the newest release, 26.2, 26.1.2, 1.21.11, 1.21.4, every 1.20.x release, and 1.8.9.
     *
     * The FIRST entry in the returned list is always the newest release.
     *
     * This talks to the internet, so it can take a moment – never call it on the UI thread
     * or the window will freeze while it waits.
     *
     * @throws IOException          if there's no internet or Mojang's server has a problem
     * @throws InterruptedException if the download is cancelled
     */
    public static List<MinecraftVersion> fetchSupportedVersions()
            throws IOException, InterruptedException {

        // 1. Download the JSON text from Mojang.
        HttpRequest request = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
                .timeout(Duration.ofSeconds(15)) // give up after 15 seconds instead of waiting forever
                .GET()
                .build();

        String json;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 200 means "OK". Anything else means the server had a problem.
            if (response.statusCode() != 200) {
                throw new IOException("Mojang's server answered with error code " + response.statusCode());
            }
            json = response.body();
        }

        // 2. Turn the JSON text into Java objects.
        ManifestJson manifest = new Gson().fromJson(json, ManifestJson.class);

        // 3. Keep only the versions we support.
        return pickSupported(manifest);
    }

    /** Filters Mojang's full list (hundreds of versions) down to the ones Nexus supports. */
    private static List<MinecraftVersion> pickSupported(ManifestJson manifest) {
        String latestId = manifest.latest().release();

        // Mojang lists versions newest first, so our list ends up newest first too:
        // e.g. 26.3, 26.2, 26.1.2, 1.21.11, 1.21.4, 1.20.6 … 1.20, 1.8.9
        List<MinecraftVersion> supported = new ArrayList<>();
        for (MinecraftVersion version : manifest.versions()) {
            if (!version.type().equals("release")) {
                continue; // skip snapshots and other test builds
            }
            String id = version.id();
            boolean wanted = id.equals(latestId)
                    || EXTRA_VERSIONS.contains(id)
                    || id.equals("1.20") || id.startsWith("1.20.")
                    || id.equals(LEGACY_VERSION);
            if (wanted) {
                supported.add(version);
            }
        }
        return supported;
    }
}
