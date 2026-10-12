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
 * ModInfo – what a mod says about itself: Fabric mods in "fabric.mod.json", Forge / NeoForge mods in
 * META-INF/mods.toml (see readForge). Read from inside its .jar:
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

    /** Reads a mod file (Fabric or Forge). Returns null if it isn't a mod (or the file is broken). */
    public static ModInfo read(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return readForge(jar, zip); // maybe a Forge / NeoForge mod
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

    // ================================================================== Forge mods

    /**
     * A Forge (or NeoForge) mod describes itself in META-INF/mods.toml (a "TOML" settings file):
     *
     *   [[mods]]
     *   modId="examplemod"
     *   version="1.2.0"            (or "${file.jarVersion}" – then the jar's manifest has it)
     *   displayName="Example Mod"
     *   [[dependencies.examplemod]]
     *   modId="jei"
     *   mandatory=true             (older Forge) or type="required" / "incompatible" (newer)
     *   versionRange="[19.0,)"
     *
     * Only the parts the scan needs are read, line by line. Returns null if it isn't a Forge mod.
     */
    private static ModInfo readForge(Path jar, ZipFile zip) throws IOException {
        ZipEntry toml = zip.getEntry("META-INF/mods.toml");
        if (toml == null) {
            toml = zip.getEntry("META-INF/neoforge.mods.toml");
        }
        if (toml == null) {
            return null;
        }
        String text;
        try (InputStream in = zip.getInputStream(toml)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        java.util.List<java.util.Map<String, String>> mods = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, String>> dependencies = new java.util.ArrayList<>();
        parseToml(text, mods, dependencies);
        if (mods.isEmpty() || !mods.get(0).containsKey("modId")) {
            return null;
        }
        java.util.Map<String, String> main = mods.get(0);
        String id = main.get("modId");
        Set<String> provides = new HashSet<>();
        mods.forEach(mod -> {
            if (mod.containsKey("modId")) {
                provides.add(mod.get("modId"));
            }
        });
        // Mods packed inside this one ("jar-in-jar": META-INF/jarjar/*.jar) count as installed too.
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry inner = entries.nextElement();
            if (inner.getName().startsWith("META-INF/jarjar/") && inner.getName().endsWith(".jar")) {
                try (InputStream in = zip.getInputStream(inner)) {
                    collectNestedForge(in.readAllBytes(), provides);
                }
            }
        }
        return forgeMod(jar, main, id, provides, dependencies, zip);
    }

    /** The ids of a Forge mod packed inside another (and any packed inside that). */
    private static void collectNestedForge(byte[] jarBytes, Set<String> provides) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("META-INF/mods.toml") || entry.getName().equals("META-INF/neoforge.mods.toml")) {
                    java.util.List<java.util.Map<String, String>> mods = new java.util.ArrayList<>();
                    parseToml(new String(zip.readAllBytes(), StandardCharsets.UTF_8), mods, new java.util.ArrayList<>());
                    mods.forEach(mod -> {
                        if (mod.containsKey("modId")) {
                            provides.add(mod.get("modId"));
                        }
                    });
                } else if (entry.getName().startsWith("META-INF/jarjar/") && entry.getName().endsWith(".jar")) {
                    collectNestedForge(zip.readAllBytes(), provides);
                }
            }
        }
    }

    /** Reads the [[mods]] and [[dependencies.x]] tables of a mods.toml (only "key = value" lines). */
    private static void parseToml(String text, java.util.List<java.util.Map<String, String>> mods,
                                  java.util.List<java.util.Map<String, String>> dependencies) {
        java.util.Map<String, String> current = null;
        boolean inLongText = false;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            // Skip multi-line texts ('''…''' or """…""", e.g. the description).
            int quotes = count(line, "'''") + count(line, "\"\"\"");
            if (inLongText) {
                inLongText = quotes % 2 == 0;
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[")) {
                line = line.replaceAll("\\s*#.*$", ""); // "[[mods]] #mandatory" → "[[mods]]"
            }
            if (line.equals("[[mods]]")) {
                current = new java.util.HashMap<>();
                mods.add(current);
            } else if (line.startsWith("[[dependencies.") && line.endsWith("]]")) {
                current = new java.util.HashMap<>();
                current.put("owner", line.substring("[[dependencies.".length(), line.length() - 2).trim());
                dependencies.add(current);
            } else if (line.startsWith("[")) {
                current = null; // some other table we don't need
            } else if (current != null && line.contains("=")) {
                String key = line.substring(0, line.indexOf('=')).trim();
                String value = line.substring(line.indexOf('=') + 1).trim();
                if (quotes % 2 == 1) {
                    inLongText = true; // a long text starts here
                    continue;
                }
                current.put(key, unquote(value));
            }
        }
    }

    /** Builds the ModInfo of a Forge mod from its main [[mods]] entry and its dependencies. */
    private static ModInfo forgeMod(Path jar, java.util.Map<String, String> main, String id, Set<String> provides,
                                    java.util.List<java.util.Map<String, String>> dependencies, ZipFile zip)
            throws IOException {
        String version = main.getOrDefault("version", "?");
        if (version.contains("${")) {
            version = manifestVersion(zip); // "${file.jarVersion}" → the jar's own version
        }
        Set<String> depends = new HashSet<>();
        java.util.Map<String, java.util.List<String>> breaks = new java.util.HashMap<>();
        for (java.util.Map<String, String> dependency : dependencies) {
            String other = dependency.get("modId");
            if (other == null || !provides.contains(dependency.get("owner"))) {
                continue;
            }
            String type = dependency.getOrDefault("type", "").toLowerCase(java.util.Locale.ROOT);
            boolean required = type.equals("required") || "true".equals(dependency.get("mandatory"));
            if (type.equals("incompatible")) {
                breaks.put(other, mavenRange(dependency.get("versionRange")));
            } else if (required) {
                depends.add(other);
            }
        }
        return new ModInfo(jar.getFileName().toString(), id, main.getOrDefault("displayName", id),
                version, depends, breaks, provides);
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    /** "\"jei\"" → "jei"; also drops a comment after the value. */
    private static String unquote(String value) {
        if (value.startsWith("\"") || value.startsWith("'")) {
            char quote = value.charAt(0);
            int end = value.indexOf(quote, 1);
            return end > 0 ? value.substring(1, end) : value.substring(1);
        }
        int comment = value.indexOf('#');
        return (comment >= 0 ? value.substring(0, comment) : value).trim();
    }

    /** The version in the jar's META-INF/MANIFEST.MF ("Implementation-Version"), or "?". */
    private static String manifestVersion(ZipFile zip) throws IOException {
        ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
        if (manifest != null) {
            try (InputStream in = zip.getInputStream(manifest)) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    if (line.startsWith("Implementation-Version:")) {
                        return line.substring("Implementation-Version:".length()).trim();
                    }
                }
            }
        }
        return "?";
    }

    /**
     * Forge writes version ranges the "Maven" way; this turns them into the rules VersionRange knows:
     * "[1.0,2.0)" → ">=1.0 <2.0", "(,1.5]" → "<=1.5", "[1.0]" → "1.0", "" or "*" → any version.
     */
    static java.util.List<String> mavenRange(String range) {
        java.util.List<String> rules = new java.util.ArrayList<>();
        if (range == null || range.isBlank() || range.trim().equals("*")) {
            return rules; // (an empty list means "any version")
        }
        String r = range.trim();
        if (!(r.startsWith("[") || r.startsWith("("))) {
            rules.add(r);
            return rules;
        }
        String inner = r.substring(1, r.length() - 1);
        if (!inner.contains(",")) {
            rules.add(inner.trim()); // exactly this version
            return rules;
        }
        String low = inner.substring(0, inner.indexOf(',')).trim();
        String high = inner.substring(inner.indexOf(',') + 1).trim();
        StringBuilder rule = new StringBuilder();
        if (!low.isEmpty()) {
            rule.append(r.startsWith("[") ? ">=" : ">").append(low);
        }
        if (!high.isEmpty()) {
            rule.append(rule.length() > 0 ? " " : "").append(r.endsWith("]") ? "<=" : "<").append(high);
        }
        rules.add(rule.length() == 0 ? "*" : rule.toString());
        return rules;
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
