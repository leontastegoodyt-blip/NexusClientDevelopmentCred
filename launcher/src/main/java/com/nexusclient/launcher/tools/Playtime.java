package com.nexusclient.launcher.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexusclient.launcher.game.NexusPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Playtime – how long the player has played, per instance and per Minecraft version
 * (the project brief: Tools → playtime stats). Saved in playtime.json in the Nexus folder.
 *
 * A "session" is one time the game was open: when the game closes, its length is added up here.
 */
public final class Playtime {

    /** One play session. start is milliseconds since 1970; seconds is how long it lasted. */
    public record Session(String instanceId, String instanceName, String version, long start, long seconds) {}

    /** The whole playtime.json. */
    public static final class Data {
        public Map<String, Long> secondsByInstance = new LinkedHashMap<>(); // instance id → seconds
        public Map<String, Long> secondsByVersion = new LinkedHashMap<>();  // "1.8.9" → seconds
        public List<Session> recentSessions = new ArrayList<>();            // newest first, up to 50
    }

    private static final int MAX_SESSIONS = 50;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = NexusPaths.root().resolve("playtime.json");

    private Playtime() {}

    /** Adds a finished play session. */
    public static synchronized void record(String instanceId, String instanceName, String version,
                                           long startMillis, long endMillis) {
        long seconds = Math.max(0, (endMillis - startMillis) / 1000);
        if (seconds < 5) {
            return; // the game closed straight away (e.g. crashed on start) – not really playtime
        }
        Data data = load();
        data.secondsByInstance.merge(instanceId, seconds, Long::sum);
        data.secondsByVersion.merge(version, seconds, Long::sum);
        data.recentSessions.add(0, new Session(instanceId, instanceName, version, startMillis, seconds));
        while (data.recentSessions.size() > MAX_SESSIONS) {
            data.recentSessions.remove(data.recentSessions.size() - 1);
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Everything recorded so far. */
    public static synchronized Data load() {
        try {
            if (Files.exists(FILE)) {
                Data data = GSON.fromJson(Files.readString(FILE), Data.class);
                if (data != null) {
                    return data;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Data();
    }

    /** All playtime added together, in seconds. */
    public static long totalSeconds() {
        return load().secondsByInstance.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 5400 seconds → "1h 30m"; 300 → "5m". */
    public static String format(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
