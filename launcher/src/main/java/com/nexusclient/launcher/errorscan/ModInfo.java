package com.nexusclient.launcher.errorscan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * ModInfo – what a Fabric mod says about itself, read from the "fabric.mod.json" file inside its .jar:
 * its id, name and version, which other mods it needs ("depends"), and which it breaks.
 *
 * Some mods carry other mods inside them ("jar-in-jar", e.g. Fabric API has dozens of small parts);
 * those count as installed too, so their ids are collected in "provides".
 *
 * @param file     the mod's file name
 * @param id       e.g. "sodium"
 * @param name     e.g. "Sodium"
 * @param version  e.g. "0.5.13"
 * @param depends  ids of mods this mod needs
 * @param breaks   mods this mod doesn't work with: id → the version rules that break (e.g. "<0.5.4")
 * @param provides every id this file makes available (its own, "provides", and mods packed inside it)
 */
public record ModInfo(String file, String id, String name, String version, Set<String> depends,
                      java.util.Map<String, java.util.List<String>> breaks, Set<String> provides) {

    /** Reads a mod file. Returns null if it isn't a Fabric mod (e.g. a Forge mod, or a broken file). */
    public static ModInfo read(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return null;
            }
            JsonObject json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            Set<String> provides = new HashSet<>();
            String id = json.get("id").getAsString();
            provides.add(id);
            addAll(provides, json.get("provides"));
            // Mods packed inside this one (listed under "jars").
            if (json.has("jars")) {
                for (JsonElement nested : json.getAsJsonArray("jars")) {
                    String path = nested.getAsJsonObject().get("file").getAsString();
                    ZipEntry inner = zip.getEntry(path);
                    if (inner != null) {
                        try (InputStream in = zip.getInputStream(inner)) {
                            collectNested(in.readAllBytes(), provides);
                        }
                    }
                }
            }
            return new ModInfo(jar.getFileName().toString(), id,
                    json.has("name") ? json.get("name").getAsString() : id,
                    json.has("version") ? json.get("version").getAsString() : "?",
                    keys(json, "depends"), rules(json, "breaks"), provides);
        } catch (IOException | RuntimeException e) {
            return null; // not readable as a Fabric mod
        }
    }

    /** Reads the ids of a mod packed inside another mod (and any packed inside that). */
    private static void collectNested(byte[] jarBytes, Set<String> provides) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("fabric.mod.json")) {
                    JsonObject json = JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    provides.add(json.get("id").getAsString());
                    addAll(provides, json.get("provides"));
                } else if (entry.getName().startsWith("META-INF/jars/") && entry.getName().endsWith(".jar")) {
                    collectNested(zip.readAllBytes(), provides);
                }
            }
        }
    }

    private static Set<String> keys(JsonObject json, String field) {
        Set<String> ids = new HashSet<>();
        if (json.has(field) && json.get(field).isJsonObject()) {
            ids.addAll(json.getAsJsonObject(field).keySet());
        }
        return ids;
    }

    /** "breaks": {"modid": "<1.0"} or {"modid": ["<1.0", "2.x"]} → modid → its version rules. */
    private static java.util.Map<String, java.util.List<String>> rules(JsonObject json, String field) {
        java.util.Map<String, java.util.List<String>> rules = new java.util.HashMap<>();
        if (json.has(field) && json.get(field).isJsonObject()) {
            for (java.util.Map.Entry<String, JsonElement> entry : json.getAsJsonObject(field).entrySet()) {
                java.util.List<String> list = new java.util.ArrayList<>();
                if (entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(rule -> list.add(rule.getAsString()));
                } else if (entry.getValue().isJsonPrimitive()) {
                    list.add(entry.getValue().getAsString());
                }
                rules.put(entry.getKey(), list);
            }
        }
        return rules;
    }

    private static void addAll(Set<String> set, JsonElement array) {
        if (array != null && array.isJsonArray()) {
            array.getAsJsonArray().forEach(element -> set.add(element.getAsString()));
        }
    }
}
