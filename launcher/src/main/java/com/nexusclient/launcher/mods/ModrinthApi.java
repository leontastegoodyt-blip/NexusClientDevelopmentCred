package com.nexusclient.launcher.mods;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ModrinthApi – talks to Modrinth (modrinth.com), the website Nexus downloads all mods, resource
 * packs, shaders and modpacks from (the project brief: mods are always downloaded from Modrinth, never
 * bundled inside Nexus).
 *
 * Modrinth has a free public "API": web addresses that answer with JSON instead of web pages.
 * Docs: https://docs.modrinth.com/api/
 */
public final class ModrinthApi {

    private static final String BASE = "https://api.modrinth.com/v2";

    /** Modrinth asks every app to say who it is, so they can contact the developer if needed. */
    private static final String USER_AGENT = "nexusclient/launcher/0.1.0 (github.com/nexusclient)";

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ------------------------------------------------------------------ the shapes of Modrinth's answers

    /** One search result. */
    public record Hit(String project_id, String slug, String title, String description, String author,
                      long downloads, String icon_url, String project_type, List<String> categories) {}

    /** A page of search results. */
    public record SearchResult(List<Hit> hits, int total_hits) {}

    /** One downloadable file of a version. */
    public record VersionFile(String url, String filename, boolean primary, long size, Map<String, String> hashes) {}

    /** Something a version needs (or doesn't work with). dependency_type: required / optional / incompatible / embedded. */
    public record Dependency(String project_id, String version_id, String dependency_type) {}

    /** One version of a project, e.g. "Sodium 0.5.13 for 1.20.1". */
    public record Version(String id, String project_id, String name, String version_number, String version_type,
                          List<String> game_versions, List<String> loaders, List<VersionFile> files,
                          List<Dependency> dependencies, String date_published) {

        /** The main file of this version (the .jar or .zip to download). */
        public VersionFile primaryFile() {
            for (VersionFile file : files) {
                if (file.primary()) {
                    return file;
                }
            }
            return files.isEmpty() ? null : files.get(0);
        }
    }

    /** A project's details. */
    public record Project(String id, String slug, String title, String description, String project_type,
                          String icon_url, long downloads) {}

    private ModrinthApi() {}

    // ------------------------------------------------------------------ requests

    /**
     * Searches Modrinth.
     *
     * @param type        "mod", "resourcepack", "shader" or "modpack"
     * @param gameVersion only show things for this Minecraft version (null = any)
     * @param loader      only show things for this loader, e.g. "fabric" (null = any)
     * @param sort        "relevance", "downloads", "updated" or "newest"
     */
    public static SearchResult search(String query, String type, String gameVersion, String loader,
                                      String sort, int offset, int limit) throws IOException, InterruptedException {
        // "Facets" are Modrinth's filters: a list of groups, where every group must match.
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:" + type));
        if (gameVersion != null) {
            facets.add(group("versions:" + gameVersion));
        }
        if (loader != null) {
            facets.add(group("categories:" + loader));
        }
        String url = BASE + "/search?query=" + encode(query == null ? "" : query)
                + "&facets=" + encode(facets.toString())
                + "&index=" + sort + "&offset=" + offset + "&limit=" + limit;
        return GSON.fromJson(get(url), SearchResult.class);
    }

    /**
     * The versions of a project that work with a Minecraft version and loader, newest first.
     *
     * @param loaders     e.g. ["fabric"], ["minecraft"] for resource packs, ["iris"] for shaders
     */
    public static List<Version> versions(String projectId, List<String> loaders, String gameVersion)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(BASE + "/project/" + encode(projectId) + "/version?");
        if (loaders != null && !loaders.isEmpty()) {
            url.append("loaders=").append(encode(GSON.toJson(loaders))).append('&');
        }
        if (gameVersion != null) {
            url.append("game_versions=").append(encode(GSON.toJson(List.of(gameVersion))));
        }
        return GSON.fromJson(get(url.toString()), new TypeToken<List<Version>>() {}.getType());
    }

    /** One specific version by its id. */
    public static Version version(String versionId) throws IOException, InterruptedException {
        return GSON.fromJson(get(BASE + "/version/" + encode(versionId)), Version.class);
    }

    /** A project's details (title, icon…). */
    public static Project project(String idOrSlug) throws IOException, InterruptedException {
        return GSON.fromJson(get(BASE + "/project/" + encode(idOrSlug)), Project.class);
    }

    /**
     * Update check: for each file fingerprint (SHA-1), the newest version that works with this
     * Minecraft version and loader. Files Modrinth doesn't know (e.g. mods the player added
     * themselves) are simply left out of the answer.
     */
    public static Map<String, Version> latestVersions(List<String> sha1s, List<String> loaders, String gameVersion)
            throws IOException, InterruptedException {
        // Modrinth also has a "check many files at once" address, but its firewall rejected it in testing,
        // so we ask about each file separately.
        JsonObject body = new JsonObject();
        body.add("loaders", GSON.toJsonTree(loaders));
        body.add("game_versions", GSON.toJsonTree(List.of(gameVersion)));
        Map<String, Version> latest = new java.util.LinkedHashMap<>();
        for (String sha1 : sha1s) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/version_file/" + sha1 + "/update?algorithm=sha1"))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            try {
                latest.put(sha1, GSON.fromJson(send(request), Version.class));
            } catch (IOException e) {
                if (!e.getMessage().startsWith("Modrinth doesn't have that")) {
                    throw e; // a real problem (not just "Modrinth doesn't know this file")
                }
            }
        }
        return latest;
    }

    // ------------------------------------------------------------------ helpers

    private static JsonArray group(String facet) {
        JsonArray group = new JsonArray();
        group.add(facet);
        return group;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String get(String url) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build());
    }

    private static String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Couldn't reach Modrinth. Check your internet connection.", e);
        }
        if (response.statusCode() == 404) {
            throw new IOException("Modrinth doesn't have that (it may have been removed).");
        }
        if (response.statusCode() == 429) {
            throw new IOException("Modrinth is busy right now. Please wait a minute and try again.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth answered with error " + response.statusCode() + ".");
        }
        return response.body();
    }
}
