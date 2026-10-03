package com.nexusclient.launcher.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.nexusclient.launcher.game.NexusPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * SettingsStore – loads the launcher's settings from settings.json when Nexus opens, and saves
 * them whenever something changes. There's only one set of settings, so this is a "singleton":
 * everything uses SettingsStore.get().
 */
public final class SettingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Path FILE = NexusPaths.root().resolve("settings.json");

    private static Settings settings;

    private SettingsStore() {}

    /** The current settings (loaded the first time this is called). */
    public static synchronized Settings get() {
        if (settings == null) {
            settings = load();
        }
        return settings;
    }

    /** Writes the current settings to settings.json. Call after changing any setting. */
    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("settings.json.tmp");
            Files.writeString(temp, GSON.toJson(get()));
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace(); // not fatal: the launcher keeps working, it just didn't save
        }
    }

    private static Settings load() {
        if (Files.exists(FILE)) {
            try {
                Settings loaded = GSON.fromJson(Files.readString(FILE), Settings.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException | JsonParseException e) {
                e.printStackTrace(); // damaged file: start with default settings
            }
        }
        return new Settings();
    }
}
