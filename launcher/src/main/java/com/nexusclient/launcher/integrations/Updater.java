package com.nexusclient.launcher.integrations;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Updater – checks whether a newer version of Nexus is out (the project brief: Nexus asks before updating
 * itself). If there is one, the launcher shows what's new and asks; saying yes opens the download.
 *
 * SETUP NEEDED: once the Nexus website exists, put a small file online like
 *   { "version": "0.2.0", "download": "https://…/NexusClient-0.2.0.exe", "notes": "What's new…" }
 * and put its address in UPDATE_URL. Until then this reports that updates aren't set up yet.
 */
public final class Updater {

    /** This copy of Nexus's version. */
    public static final String CURRENT_VERSION = "0.1.0";

    /** Where the latest-version file lives. Null = not set up yet (see the note above). */
    public static final String UPDATE_URL = null;

    /** What the update file says. */
    public record UpdateInfo(String version, String download, String notes) {}

    private Updater() {}

    public static boolean isConfigured() {
        return UPDATE_URL != null;
    }

    /**
     * Asks the website for the newest version. Returns it if it's newer than this one, or null.
     * Run on a background thread.
     */
    public static UpdateInfo check() throws IOException, InterruptedException {
        if (!isConfigured()) {
            return null;
        }
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(UPDATE_URL)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("The update server answered with error " + response.statusCode());
            }
            UpdateInfo latest = new Gson().fromJson(response.body(), UpdateInfo.class);
            return isNewer(latest.version(), CURRENT_VERSION) ? latest : null;
        }
    }

    /** Compares version numbers part by part: "0.10.0" is newer than "0.9.3". */
    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("\\.");
        String[] b = current.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parse(a[i]) : 0;
            int y = i < b.length ? parse(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
