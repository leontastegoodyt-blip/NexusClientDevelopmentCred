package com.nexusclient.launcher.rerender;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.game.ProgressListener;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.mods.ContentType;
import com.nexusclient.launcher.mods.ModManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Workspace – Mod Rerender's picture of one instance's setup, which other players can look at and copy
 * (only while its owner has "Let other players render my workspace" switched on).
 *
 * What's in it (JSON):
 *   "instance": name, version choice, the real game version, mod loader
 *   "content":  every mod, resource pack and shader – as Modrinth references (project + exact version),
 *               NEVER the files themselves (Nexus never hands out other people's downloads). Files the
 *               player added themselves are only listed by name ("own": true) and can't be copied.
 *   "files":    small text files: options.txt (game settings), config/… (mod settings) and the Nexus
 *               HUD settings. Anything that might be private is left out: files whose name or text
 *               mentions keys, tokens, passwords, webhooks…, and the player's waypoints (base locations).
 *
 * Using someone's workspace always backs up what it replaces first (<instance>/nexus-backups/).
 */
public final class Workspace {

    public static final int FORMAT = 1;

    private static final Set<String> TEXT_FILES = Set.of(".json", ".json5", ".toml", ".properties", ".txt", ".cfg",
            ".yml", ".yaml", ".ini", ".conf", ".snbt", ".jsonc");
    private static final int MAX_FILE = 96 * 1024;
    private static final int MAX_TOTAL = 1536 * 1024;
    /** Words that suggest a secret: such files (by name or contents) are never shared. */
    private static final Pattern SECRET = Pattern.compile(
            "(api[_-]?key|apikey|token|secret|passw|webhook|credential|session[_-]?id|auth[_-]?key|private[_-]?key)",
            Pattern.CASE_INSENSITIVE);
    /** Files that are personal rather than "setup". */
    private static final Set<String> PERSONAL = Set.of("config/nexus/waypoints.json");

    private Workspace() {}

    // ================================================================== making one (sharing)

    /**
     * The workspace of an instance.
     *
     * @param gameVersion the instance's real Minecraft version (e.g. "26.3" when it follows "Latest")
     */
    public static JsonObject build(Instance instance, String gameVersion) throws IOException {
        JsonObject workspace = new JsonObject();
        workspace.addProperty("format", FORMAT);
        JsonObject info = new JsonObject();
        info.addProperty("name", instance.name());
        info.addProperty("versionChoice", instance.version());
        info.addProperty("gameVersion", gameVersion);
        info.addProperty("loader", instance.loaderOrVanilla());
        workspace.add("instance", info);

        ModManager mods = new ModManager(instance, gameVersion);
        JsonArray content = new JsonArray();
        for (ContentType type : List.of(ContentType.MOD, ContentType.RESOURCE_PACK, ContentType.SHADER)) {
            for (ModManager.Installed item : mods.list(type)) {
                if (item.fileName().startsWith("nexus-client")) {
                    continue; // the Nexus mod itself – every Nexus player gets it anyway
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("type", type.name());
                entry.addProperty("title", item.displayName());
                entry.addProperty("enabled", item.enabled());
                if (item.entry() != null) {
                    entry.addProperty("projectId", item.entry().projectId());
                    entry.addProperty("versionId", item.entry().versionId());
                    entry.addProperty("versionNumber", item.entry().versionNumber());
                } else {
                    entry.addProperty("own", true); // added by the player – listed, not copyable
                }
                content.add(entry);
            }
        }
        workspace.add("content", content);

        JsonObject files = new JsonObject();
        Path dir = NexusPaths.instanceDir(instance);
        int[] total = {0};
        addFile(dir, "options.txt", files, total);
        Path config = dir.resolve("config");
        if (Files.isDirectory(config)) {
            try (Stream<Path> walk = Files.walk(config, 6)) {
                for (Path file : (Iterable<Path>) walk.sorted()::iterator) {
                    if (Files.isRegularFile(file)) {
                        addFile(dir, dir.relativize(file).toString().replace('\\', '/'), files, total);
                    }
                }
            }
        }
        workspace.add("files", files);
        return workspace;
    }

    /** Adds one text file if it's small, safe to share and there's room. */
    private static void addFile(Path dir, String relative, JsonObject files, int[] total) {
        String lower = relative.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || !TEXT_FILES.contains(lower.substring(dot)) || PERSONAL.contains(lower)
                || SECRET.matcher(lower).find() || lower.contains("/.") || lower.contains("backup")) {
            return;
        }
        try {
            Path file = dir.resolve(relative);
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE || total[0] + Files.size(file) > MAX_TOTAL) {
                return;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (SECRET.matcher(text).find()) {
                return; // might hold something private – never shared
            }
            if (lower.equals("options.txt")) {
                text = text.replaceAll("(?m)^lastServer:.*\\R?", ""); // which server you last joined
            }
            files.addProperty(relative, text);
            total[0] += text.length();
        } catch (IOException | RuntimeException e) {
            // unreadable or not text – skip it
        }
    }

    // ================================================================== reading one

    public static JsonObject info(JsonObject workspace) {
        return workspace.has("instance") ? workspace.getAsJsonObject("instance") : new JsonObject();
    }

    public static List<JsonObject> content(JsonObject workspace, ContentType type) {
        List<JsonObject> list = new ArrayList<>();
        if (workspace.has("content")) {
            for (JsonElement element : workspace.getAsJsonArray("content")) {
                JsonObject entry = element.getAsJsonObject();
                if (type.name().equals(str(entry, "type"))) {
                    list.add(entry);
                }
            }
        }
        return list;
    }

    /** The shared text files (path → text). */
    public static JsonObject files(JsonObject workspace) {
        return workspace.has("files") ? workspace.getAsJsonObject("files") : new JsonObject();
    }

    public static boolean hasHud(JsonObject workspace) {
        return files(workspace).has("config/nexus/nexus.json");
    }

    // ================================================================== using one

    /** Their mods (exact versions) into an instance – yours stay, the same mod is replaced. */
    public static int useMods(JsonObject workspace, Instance target, String gameVersion, ProgressListener progress)
            throws IOException, InterruptedException {
        return install(workspace, target, gameVersion, List.of(ContentType.MOD), progress);
    }

    /** Everything copyable from Modrinth: mods, resource packs and shaders. */
    public static int useContent(JsonObject workspace, Instance target, String gameVersion, ProgressListener progress)
            throws IOException, InterruptedException {
        return install(workspace, target, gameVersion,
                List.of(ContentType.MOD, ContentType.RESOURCE_PACK, ContentType.SHADER), progress);
    }

    private static int install(JsonObject workspace, Instance target, String gameVersion, List<ContentType> types,
                               ProgressListener progress) throws IOException, InterruptedException {
        ModManager mods = new ModManager(target, gameVersion);
        int done = 0;
        int count = 0;
        for (ContentType type : types) {
            count += (int) content(workspace, type).stream().filter(e -> e.has("versionId")).count();
        }
        for (ContentType type : types) {
            for (JsonObject entry : content(workspace, type)) {
                if (!entry.has("versionId")) {
                    continue; // their own file – can't be copied
                }
                progress.update("Installing " + str(entry, "title") + "…", count == 0 ? -1 : done / (double) count);
                mods.installVersion(str(entry, "versionId"), type, str(entry, "title"),
                        !entry.has("enabled") || entry.get("enabled").getAsBoolean());
                done++;
            }
        }
        return done;
    }

    /** Their config files and game settings (not the Nexus HUD). Returns how many files were written. */
    public static int useConfig(JsonObject workspace, Instance target) throws IOException {
        return writeFiles(workspace, target, path -> !path.equals("config/nexus/nexus.json"));
    }

    /** Their Nexus HUD layout and in-game Nexus settings. */
    public static int useHud(JsonObject workspace, Instance target) throws IOException {
        return writeFiles(workspace, target, path -> path.equals("config/nexus/nexus.json"));
    }

    /** All of their shared files (for a brand-new copy of their instance). */
    public static int useAllFiles(JsonObject workspace, Instance target) throws IOException {
        return writeFiles(workspace, target, path -> true);
    }

    private interface PathFilter {
        boolean wanted(String path);
    }

    /** Writes shared files into the instance, backing up any file it replaces. */
    private static int writeFiles(JsonObject workspace, Instance target, PathFilter filter) throws IOException {
        Path dir = NexusPaths.instanceDir(target).toAbsolutePath().normalize();
        Path backup = dir.resolve("nexus-backups").resolve("rerender-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
        int written = 0;
        for (var entry : files(workspace).entrySet()) {
            String relative = entry.getKey();
            if (!filter.wanted(relative)) {
                continue;
            }
            Path file = dir.resolve(relative).normalize();
            // Only options.txt and files inside config/ – a crafted workspace can't write anywhere else.
            if (!file.startsWith(dir.resolve("config")) && !file.equals(dir.resolve("options.txt"))) {
                continue;
            }
            if (Files.exists(file)) {
                Path saved = backup.resolve(relative);
                Files.createDirectories(saved.getParent());
                Files.copy(file, saved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue().getAsString(), StandardCharsets.UTF_8);
            written++;
        }
        return written;
    }

    static String str(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
