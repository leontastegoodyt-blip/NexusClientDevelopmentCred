package com.nexusclient.launcher.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nexusclient.launcher.game.Downloader;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.game.ProgressListener;
import com.nexusclient.launcher.instance.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ModManager – adds, updates, switches on/off and removes mods, resource packs and shaders for one
 * instance.
 *
 * It keeps a small record (nexus-mods.json in the instance folder) of which Modrinth project each
 * downloaded file came from, so it can find dependencies and updates. Files the player added
 * themselves work too – they just can't be updated automatically.
 *
 * Most methods talk to the internet or copy files, so call them from a background thread.
 */
public final class ModManager {

    /** One entry in nexus-mods.json: where a file came from. */
    public record IndexEntry(String projectId, String versionId, String title, String versionNumber, ContentType type) {}

    /** The whole nexus-mods.json. */
    private static final class Index {
        Map<String, IndexEntry> files = new LinkedHashMap<>(); // file name → where it came from
        boolean performanceModsAdded = false;
    }

    /**
     * Something installed in the instance, as shown in the Installed list.
     *
     * @param fileName   the file's current name (mods that are switched off end in ".disabled")
     * @param enabled    for mods: switched on; for resource packs: turned on in the game settings
     * @param entry      where it came from (null for files the player added themselves)
     */
    public record Installed(ContentType type, String fileName, String displayName, boolean enabled, IndexEntry entry) {}

    /** The performance mods Nexus adds to Fabric instances (the project brief: auto-installs FPS-boost mods). */
    private static final List<String> PERFORMANCE_MODS = List.of("sodium", "lithium", "ferrite-core");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Instance instance;
    private final String gameVersion;
    private final Path dir;

    /**
     * @param gameVersion the actual Minecraft version the instance plays (e.g. "1.20.1")
     */
    public ModManager(Instance instance, String gameVersion) {
        this.instance = instance;
        this.gameVersion = gameVersion;
        this.dir = NexusPaths.instanceDir(instance);
    }

    // ================================================================== what works with what

    /**
     * Modrinth's loader name for this instance's mods: "fabric", or "legacy-fabric" for old versions
     * (like 1.8.9) that use Legacy Fabric. Null if the instance has no mod loader (Vanilla).
     */
    public String modLoader() {
        if (!Instance.FABRIC.equals(instance.loaderOrVanilla())) {
            return null;
        }
        return isOldVersion() ? "legacy-fabric" : "fabric";
    }

    /** True for versions before 1.14 (they use Legacy Fabric, and have no Iris shaders). */
    private boolean isOldVersion() {
        if (!gameVersion.startsWith("1.")) {
            return false;
        }
        try {
            return Integer.parseInt(gameVersion.split("\\.")[1]) < 14;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Can this instance use this kind of content? If not, returns a plain-English reason. */
    public String whyNot(ContentType type) {
        return switch (type) {
            case MOD -> modLoader() == null
                    ? "Mods need a mod loader. Switch \"" + instance.name() + "\" to Fabric in its settings." : null;
            case SHADER -> modLoader() == null || isOldVersion()
                    ? "Shaders need Fabric on Minecraft 1.16 or newer (they run through the Iris mod)." : null;
            default -> null;
        };
    }

    /** Modrinth's loader list for a type of content. */
    private List<String> loadersFor(ContentType type) {
        return switch (type) {
            case MOD -> List.of(modLoader());
            case RESOURCE_PACK -> List.of("minecraft");
            case SHADER -> List.of("iris");
            default -> List.of();
        };
    }

    // ================================================================== installing

    /**
     * Installs the newest version of a Modrinth project that works with this instance, plus
     * everything it requires. Replaces an older copy of the same project.
     *
     * @return the name of what was installed
     */
    public String install(String projectId, ContentType type, ProgressListener progress)
            throws IOException, InterruptedException {
        String reason = whyNot(type);
        if (reason != null) {
            throw new IOException(reason);
        }
        Index index = readIndex();
        Set<String> visiting = new HashSet<>();
        String title = installProject(projectId, type, index, visiting, progress);
        // Shaders only work through the Iris mod – make sure it's there too.
        if (type == ContentType.SHADER && !hasProject(index, "YL57xq9U") && !hasSlug(index, "iris")) {
            installProject("iris", ContentType.MOD, index, visiting, progress);
        }
        writeIndex(index);
        return title;
    }

    /** Installs one project and (for mods) its required dependencies. */
    private String installProject(String projectId, ContentType type, Index index, Set<String> visiting,
                                  ProgressListener progress) throws IOException, InterruptedException {
        if (!visiting.add(projectId)) {
            return projectId; // already being installed (dependencies can point at each other)
        }
        List<ModrinthApi.Version> versions = ModrinthApi.versions(projectId, loadersFor(type), gameVersion);
        if (versions.isEmpty()) {
            throw new IOException("There's no version of this for Minecraft " + gameVersion
                    + (type == ContentType.MOD ? " with " + instance.loaderLabel() : "") + " yet.");
        }
        ModrinthApi.Version chosen = pickVersion(versions);
        ModrinthApi.Project project = ModrinthApi.project(chosen.project_id());
        progress.update("Installing " + project.title() + "…", -1);

        // Required dependencies first (only mods have them).
        if (type == ContentType.MOD && chosen.dependencies() != null) {
            for (ModrinthApi.Dependency dependency : chosen.dependencies()) {
                if ("required".equals(dependency.dependency_type()) && dependency.project_id() != null
                        && !hasProject(index, dependency.project_id())) {
                    installProject(dependency.project_id(), ContentType.MOD, index, visiting, progress);
                }
            }
        }

        downloadVersion(chosen, project.title(), type, index);
        return project.title();
    }

    /** Prefers the newest full release; falls back to the newest beta/alpha if there's no release. */
    private static ModrinthApi.Version pickVersion(List<ModrinthApi.Version> versions) {
        for (ModrinthApi.Version version : versions) {
            if ("release".equals(version.version_type())) {
                return version;
            }
        }
        return versions.get(0);
    }

    /** Downloads a version's main file into the right folder, replacing an older copy of it. */
    private void downloadVersion(ModrinthApi.Version version, String title, ContentType type, Index index)
            throws IOException, InterruptedException {
        ModrinthApi.VersionFile file = version.primaryFile();
        if (file == null) {
            throw new IOException(title + " has no file to download.");
        }
        Path folder = dir.resolve(type.folder());
        Files.createDirectories(folder);
        String fileName = Path.of(file.filename()).getFileName().toString(); // no sneaky folders in the name

        // Remove the older copy of the same project (including a switched-off one).
        for (Map.Entry<String, IndexEntry> old : new ArrayList<>(index.files.entrySet())) {
            if (old.getValue().projectId().equals(version.project_id()) && !old.getKey().equals(fileName)) {
                Files.deleteIfExists(folder.resolve(old.getKey()));
                Files.deleteIfExists(folder.resolve(old.getKey() + ".disabled"));
                index.files.remove(old.getKey());
            }
        }

        try (Downloader downloader = new Downloader()) {
            downloader.download(new Downloader.Job(file.url(), folder.resolve(fileName),
                    file.hashes() == null ? null : file.hashes().get("sha1"), file.size()));
        }
        index.files.put(fileName, new IndexEntry(version.project_id(), version.id(), title,
                version.version_number(), type));
    }

    private static boolean hasProject(Index index, String projectId) {
        return index.files.values().stream().anyMatch(entry -> entry.projectId().equals(projectId));
    }

    private static boolean hasSlug(Index index, String slug) {
        return index.files.values().stream().anyMatch(entry -> entry.title().equalsIgnoreCase(slug));
    }

    /** The Modrinth projects already installed (so the browser can show "Installed"). */
    public Set<String> installedProjectIds() {
        Set<String> ids = new HashSet<>();
        for (IndexEntry entry : readIndex().files.values()) {
            ids.add(entry.projectId());
        }
        return ids;
    }

    /**
     * Adds Nexus's performance mods (Sodium, Lithium, FerriteCore) to a Fabric instance, once.
     * If the player removes them later, they're not added again. Problems are ignored – the game
     * still starts without them.
     */
    public void ensurePerformanceMods(ProgressListener progress) throws InterruptedException {
        if (modLoader() == null || isOldVersion()) {
            return; // no Fabric, or 1.8.9 (these mods don't exist for it)
        }
        Index index = readIndex();
        if (index.performanceModsAdded) {
            return;
        }
        for (String mod : PERFORMANCE_MODS) {
            try {
                installProject(mod, ContentType.MOD, index, new HashSet<>(), progress);
            } catch (IOException e) {
                System.err.println("Skipped performance mod " + mod + ": " + e.getMessage());
            }
        }
        index.performanceModsAdded = true;
        try {
            writeIndex(index);
        } catch (IOException e) {
            e.printStackTrace(); // not fatal: they'd just be checked again next launch
        }
    }

    /** The Minecraft versions the in-game Nexus mod is built for so far (1.20.x and more come later). */
    private static final Set<String> NEXUS_MOD_VERSIONS = Set.of("26.3");

    /** The file name the Nexus mod gets in an instance's mods folder. */
    public static final String NEXUS_MOD_FILE = "nexus-client.jar";

    /**
     * Puts the in-game Nexus mod (mod menu, HUD, PvP mods…) into Fabric instances it's made for,
     * plus Fabric API, which it needs. The mod ships inside the launcher, and is replaced every
     * launch if the launcher has a newer copy. Problems are printed – the game still starts.
     */
    public void ensureNexusMod(ProgressListener progress) throws InterruptedException {
        if (modLoader() == null || !NEXUS_MOD_VERSIONS.contains(gameVersion)) {
            return;
        }
        try (InputStream bundled = ModManager.class.getResourceAsStream(
                "/com/nexusclient/launcher/nexus-mod/nexus-client-fabric-" + gameVersion + ".jar")) {
            if (bundled == null) {
                System.err.println("This launcher build has no Nexus mod for " + gameVersion);
                return;
            }
            byte[] jar = bundled.readAllBytes();
            Path mods = dir.resolve("mods");
            Files.createDirectories(mods);
            Path target = mods.resolve(NEXUS_MOD_FILE);
            if (!Files.exists(target) || !java.util.Arrays.equals(Files.readAllBytes(target), jar)) {
                Files.write(target, jar);
            }
            // A turned-off copy ("nexus-client.jar.disabled") would clash with the fresh one.
            Files.deleteIfExists(mods.resolve(NEXUS_MOD_FILE + ".disabled"));

            boolean hasFabricApi;
            try (Stream<Path> files = Files.list(mods)) {
                hasFabricApi = files.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                        .startsWith("fabric-api") && p.getFileName().toString().endsWith(".jar"));
            }
            if (!hasFabricApi) {
                Index index = readIndex();
                installProject("fabric-api", ContentType.MOD, index, new HashSet<>(), progress);
                writeIndex(index);
            }
        } catch (IOException e) {
            System.err.println("Couldn't set up the Nexus mod: " + e.getMessage());
        }
    }

    // ================================================================== what's installed

    /** Everything of one type in the instance, including files the player added themselves. */
    public List<Installed> list(ContentType type) throws IOException {
        Path folder = dir.resolve(type.folder());
        List<Installed> items = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return items;
        }
        Index index = readIndex();
        List<String> enabledPacks = type == ContentType.RESOURCE_PACK ? enabledResourcePacks() : List.of();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path path : files.sorted().toList()) {
                String name = path.getFileName().toString();
                boolean isFolder = Files.isDirectory(path);
                switch (type) {
                    case MOD -> {
                        if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) continue;
                    }
                    case RESOURCE_PACK, SHADER -> {
                        if (!isFolder && !name.endsWith(".zip")) continue;
                    }
                    default -> { continue; }
                }
                boolean disabled = name.endsWith(".disabled");
                String baseName = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;
                IndexEntry entry = index.files.get(baseName);
                String display = entry != null ? entry.title() : baseName.replaceAll("\\.(jar|zip)$", "");
                boolean enabled = switch (type) {
                    case MOD -> !disabled;
                    case RESOURCE_PACK -> enabledPacks.contains(name);
                    case SHADER -> name.equals(selectedShader());
                    default -> false;
                };
                items.add(new Installed(type, name, display, enabled, entry));
            }
        }
        return items;
    }

    /** Where an installed item's file (or folder) is. */
    public Path fileOf(Installed item) {
        return dir.resolve(item.type().folder()).resolve(item.fileName());
    }

    /** Switches a mod on or off (off = the file is renamed to end in ".disabled", so the game skips it). */
    public void setModEnabled(Installed mod, boolean enabled) throws IOException {
        Path folder = dir.resolve(ContentType.MOD.folder());
        String baseName = mod.fileName().replaceAll("\\.disabled$", "");
        Path on = folder.resolve(baseName);
        Path off = folder.resolve(baseName + ".disabled");
        if (enabled && Files.exists(off)) {
            Files.move(off, on, StandardCopyOption.REPLACE_EXISTING);
        } else if (!enabled && Files.exists(on)) {
            Files.move(on, off, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes an installed file (or pack folder). */
    public void remove(Installed item) throws IOException {
        Path path = dir.resolve(item.type().folder()).resolve(item.fileName());
        if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.walk(path)) {
                for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
        Index index = readIndex();
        index.files.remove(item.fileName().replaceAll("\\.disabled$", ""));
        writeIndex(index);
        if (item.type() == ContentType.RESOURCE_PACK && item.enabled()) {
            setResourcePackEnabled(item.fileName(), false);
        }
    }

    /** Copies the player's own files into the right folder. */
    public void addOwnFiles(List<Path> files, ContentType type) throws IOException {
        Path folder = dir.resolve(type.folder());
        Files.createDirectories(folder);
        for (Path file : files) {
            Files.copy(file, folder.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ================================================================== updates

    /**
     * Checks Modrinth for newer versions of everything of one type.
     *
     * @return file name → the newer version, only for files that have an update
     */
    public Map<String, ModrinthApi.Version> checkUpdates(ContentType type) throws IOException, InterruptedException {
        if (whyNot(type) != null) {
            return Map.of();
        }
        Path folder = dir.resolve(type.folder());
        Map<String, String> hashToFile = new LinkedHashMap<>();
        for (Installed item : list(type)) {
            Path file = folder.resolve(item.fileName());
            if (Files.isRegularFile(file)) {
                hashToFile.put(Downloader.sha1(file), item.fileName());
            }
        }
        if (hashToFile.isEmpty()) {
            return Map.of();
        }
        Map<String, ModrinthApi.Version> latest = ModrinthApi.latestVersions(
                new ArrayList<>(hashToFile.keySet()), loadersFor(type), gameVersion);
        Map<String, ModrinthApi.Version> updates = new LinkedHashMap<>();
        for (Map.Entry<String, ModrinthApi.Version> entry : latest.entrySet()) {
            ModrinthApi.VersionFile newest = entry.getValue().primaryFile();
            boolean different = newest != null && newest.hashes() != null
                    && !entry.getKey().equalsIgnoreCase(newest.hashes().get("sha1"));
            if (different) {
                updates.put(hashToFile.get(entry.getKey()), entry.getValue());
            }
        }
        return updates;
    }

    /** Installs a newer version found by checkUpdates(). Keeps a switched-off mod switched off. */
    public void update(Installed item, ModrinthApi.Version newer) throws IOException, InterruptedException {
        boolean wasDisabled = item.type() == ContentType.MOD && !item.enabled();
        Index index = readIndex();
        String baseName = item.fileName().replaceAll("\\.disabled$", "");
        if (!index.files.containsKey(baseName)) {
            // A file the player added themselves: remember where Modrinth says it came from.
            index.files.put(baseName, new IndexEntry(newer.project_id(), "", item.displayName(), "", item.type()));
        }
        downloadVersion(newer, index.files.get(baseName).title(), item.type(), index);
        writeIndex(index);
        if (wasDisabled) {
            ModrinthApi.VersionFile file = newer.primaryFile();
            setModEnabled(new Installed(ContentType.MOD, file.filename(), item.displayName(), true, null), false);
        }
    }

    // ================================================================== resource packs

    /**
     * The resource packs switched on in the game's settings (options.txt), highest priority first.
     * Minecraft stores them as a list; newer versions put "file/" in front and list them lowest
     * priority first, 1.8.9 lists them highest first without "file/".
     */
    public List<String> enabledResourcePacks() throws IOException {
        List<String> stored = readPackList();
        List<String> packs = new ArrayList<>();
        if (isOldVersion()) {
            packs.addAll(stored);
        } else {
            for (String entry : stored) {
                if (entry.startsWith("file/")) {
                    packs.add(0, entry.substring("file/".length())); // reverse: highest first
                }
            }
        }
        return packs;
    }

    /** Switches a resource pack on (at the top) or off. */
    public void setResourcePackEnabled(String packName, boolean enabled) throws IOException {
        List<String> packs = enabledResourcePacks();
        packs.remove(packName);
        if (enabled) {
            packs.add(0, packName);
        }
        writePackList(packs);
    }

    /** Moves an enabled resource pack up (more important) or down. */
    public void moveResourcePack(String packName, boolean up) throws IOException {
        List<String> packs = enabledResourcePacks();
        int index = packs.indexOf(packName);
        int target = up ? index - 1 : index + 1;
        if (index < 0 || target < 0 || target >= packs.size()) {
            return;
        }
        packs.remove(index);
        packs.add(target, packName);
        writePackList(packs);
    }

    private List<String> readPackList() throws IOException {
        Path options = dir.resolve("options.txt");
        List<String> list = new ArrayList<>();
        if (!Files.exists(options)) {
            return list;
        }
        for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
            if (line.startsWith("resourcePacks:")) {
                try {
                    JsonArray array = JsonParser.parseString(line.substring("resourcePacks:".length())).getAsJsonArray();
                    for (JsonElement element : array) {
                        list.add(element.getAsString());
                    }
                } catch (RuntimeException e) {
                    // damaged line – treat as empty
                }
            }
        }
        return list;
    }

    /** Writes the enabled packs (highest first) back into options.txt in the right format. */
    private void writePackList(List<String> packsHighestFirst) throws IOException {
        List<String> stored = new ArrayList<>();
        if (isOldVersion()) {
            stored.addAll(packsHighestFirst);
        } else {
            // Keep the built-in entries (like "vanilla") first, then our packs lowest priority first.
            for (String entry : readPackList()) {
                if (!entry.startsWith("file/")) {
                    stored.add(entry);
                }
            }
            if (!stored.contains("vanilla")) {
                stored.add(0, "vanilla");
            }
            for (int i = packsHighestFirst.size() - 1; i >= 0; i--) {
                stored.add("file/" + packsHighestFirst.get(i));
            }
        }
        Path options = dir.resolve("options.txt");
        List<String> lines = Files.exists(options) ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8))
                : new ArrayList<>();
        String newLine = "resourcePacks:" + GSON.toJson(stored).replaceAll("\\s+", "");
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("resourcePacks:")) {
                lines.set(i, newLine);
                replaced = true;
            }
        }
        if (!replaced) {
            lines.add(newLine);
        }
        Files.createDirectories(options.getParent());
        Files.write(options, lines, StandardCharsets.UTF_8);
    }

    // ================================================================== shaders (through Iris)

    private Path irisConfig() {
        return dir.resolve("config").resolve("iris.properties");
    }

    private Properties readIris() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(irisConfig())) {
            try (var in = Files.newInputStream(irisConfig())) {
                properties.load(in);
            }
        }
        return properties;
    }

    /** True if shaders are switched on (Iris's setting). */
    public boolean shadersEnabled() throws IOException {
        return Boolean.parseBoolean(readIris().getProperty("enableShaders", "false"));
    }

    /** The shader pack Iris will use, or null. */
    public String selectedShader() throws IOException {
        return readIris().getProperty("shaderPack");
    }

    /** The simple shaders on/off switch (the project brief), and which shader pack to use. */
    public void setShaders(boolean enabled, String shaderPack) throws IOException {
        Properties properties = readIris();
        properties.setProperty("enableShaders", String.valueOf(enabled));
        if (shaderPack != null) {
            properties.setProperty("shaderPack", shaderPack);
        }
        Files.createDirectories(irisConfig().getParent());
        try (var out = Files.newOutputStream(irisConfig())) {
            properties.store(out, "Written by Nexus Client");
        }
    }

    // ================================================================== nexus-mods.json

    private Index readIndex() {
        Path file = dir.resolve("nexus-mods.json");
        try {
            if (Files.exists(file)) {
                Index index = GSON.fromJson(Files.readString(file), Index.class);
                if (index != null && index.files != null) {
                    return index;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Index();
    }

    private void writeIndex(Index index) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("nexus-mods.json"), GSON.toJson(index));
    }
}
