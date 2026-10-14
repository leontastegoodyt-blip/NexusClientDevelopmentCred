package com.nexusclient.launcher.torcherrig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * OptionsFile – reads and changes Minecraft's options.txt (the game's settings file).
 *
 * options.txt is simple: one setting per line, like "renderDistance:12". But the setting NAMES
 * changed over the years, so there are three "families":
 *
 *   LEGACY  – 1.8.9:      fancyGraphics, renderClouds, ao (0/1/2), maxFps
 *   MIDDLE  – 1.16–1.21:  graphicsMode (0/1/2), renderClouds ("true"/"fast"/"false"), ao (true/false), maxFps
 *   NEW     – 26.x:       graphicsPreset ("fast"/"fancy"/"fabulous"); the rest as MIDDLE
 *
 * (The NEW names were checked in options.txt files written by Minecraft 26.1.2, 26.2 and 26.3
 * themselves. Check again if a future version changes them.)
 *
 * The file also has a "version" line – Minecraft's "data version". If it's missing, Minecraft
 * assumes the file is ancient and tries to convert it, so we always write the right one. It comes
 * from version.json inside the game's .jar file.
 */
public final class OptionsFile {

    enum Family { LEGACY, MIDDLE, NEW }

    private OptionsFile() {}

    /**
     * Writes a TorcherRig plan into an options.txt, keeping every other setting the player has.
     *
     * @param file      the instance's options.txt (created if it doesn't exist)
     * @param versionId the Minecraft version the instance plays, e.g. "1.8.9" or "26.3"
     * @param clientJar the game's .jar file (to read its data version), or null if not downloaded
     */
    public static void apply(Path file, GameSettingsPlan plan, String versionId, Path clientJar) throws IOException {
        Map<String, String> options = read(file);
        Family family = detectFamily(options, versionId);

        if (!options.containsKey("version")) {
            Integer dataVersion = dataVersion(clientJar);
            if (dataVersion != null) {
                Map<String, String> withVersion = new LinkedHashMap<>();
                withVersion.put("version", String.valueOf(dataVersion)); // Minecraft writes it first
                withVersion.putAll(options);
                options = withVersion;
            }
        }

        options.put("renderDistance", String.valueOf(family == Family.LEGACY
                ? Math.min(plan.renderDistance(), 16) // 1.8.9 can't go higher without mods
                : plan.renderDistance()));
        options.put("particles", switch (plan.particles()) {
            case "ALL" -> "0";
            case "DECREASED" -> "1";
            default -> "2"; // MINIMAL
        });
        options.put("entityShadows", String.valueOf(plan.entityShadows()));

        switch (family) {
            case LEGACY -> {
                options.put("fancyGraphics", String.valueOf(!plan.graphics().equals("FAST")));
                options.put("renderClouds", clouds(plan, false));
                options.put("ao", plan.smoothLighting() ? "2" : "0");
                options.put("maxFps", String.valueOf(plan.maxFps()));
            }
            case MIDDLE -> {
                options.put("simulationDistance", String.valueOf(plan.simulationDistance()));
                options.put("graphicsMode", switch (plan.graphics()) {
                    case "FAST" -> "0";
                    case "FANCY" -> "1";
                    default -> "2"; // FABULOUS
                });
                options.put("renderClouds", clouds(plan, true));
                options.put("ao", String.valueOf(plan.smoothLighting()));
                options.put("maxFps", String.valueOf(plan.maxFps()));
            }
            case NEW -> {
                options.put("simulationDistance", String.valueOf(plan.simulationDistance()));
                options.put("graphicsPreset", "\"" + plan.graphics().toLowerCase() + "\"");
                options.put("renderClouds", clouds(plan, true));
                options.put("ao", String.valueOf(plan.smoothLighting()));
                options.put("maxFps", String.valueOf(plan.maxFps()));
                // Names from an older guess that the game never read – remove them so the file stays clean.
                options.remove("cloudStatus");
                options.remove("ambientOcclusion");
                options.remove("framerateLimit");
            }
        }
        write(file, options);
    }

    /** Works out which family of names a file uses – from the file itself if possible. */
    static Family detectFamily(Map<String, String> options, String versionId) {
        if (options.containsKey("graphicsPreset") || options.containsKey("framerateLimit")
                || options.containsKey("cloudStatus") || options.containsKey("ambientOcclusion")) {
            return Family.NEW;
        }
        if (options.containsKey("graphicsMode")) {
            return Family.MIDDLE;
        }
        if (options.containsKey("fancyGraphics")) {
            return Family.LEGACY;
        }
        // No file yet: go by the version number. "1.x" versions before 1.16 used the old names.
        if (versionId.startsWith("1.8") || versionId.startsWith("1.12")) {
            return Family.LEGACY;
        }
        return versionId.startsWith("1.") ? Family.MIDDLE : Family.NEW;
    }

    /** Cloud setting text. Newer versions put quotes around it. */
    private static String clouds(GameSettingsPlan plan, boolean quoted) {
        String value = switch (plan.clouds()) {
            case "OFF" -> "false";
            case "FAST" -> "fast";
            default -> "true"; // FANCY
        };
        return quoted ? "\"" + value + "\"" : value;
    }

    /** Reads options.txt into an ordered map (empty if the file doesn't exist). */
    static Map<String, String> read(Path file) throws IOException {
        Map<String, String> options = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return options;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                options.put(line.substring(0, colon), line.substring(colon + 1));
            }
        }
        return options;
    }

    private static void write(Path file, Map<String, String> options) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> option : options.entrySet()) {
            text.append(option.getKey()).append(':').append(option.getValue()).append('\n');
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Reads Minecraft's "data version" from version.json inside the game .jar
     * (only exists in 1.14 and newer). Returns null if it can't be found.
     */
    static Integer dataVersion(Path clientJar) {
        if (clientJar == null || !Files.exists(clientJar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            ZipEntry entry = zip.getEntry("version.json");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("world_version").getAsInt();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
