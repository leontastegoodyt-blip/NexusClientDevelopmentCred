package com.nexusclient.launcher.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nexusclient.launcher.game.NexusPaths;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * InstanceStore – keeps the player's instances and saves them to a file, so they're still there
 * (with all their choices) the next time the launcher opens. Also does the bigger jobs:
 * duplicate, delete, export, import and setup codes.
 *
 * The file is instances.json in the Nexus folder, e.g. on Windows:
 *   C:\Users\<you>\AppData\Roaming\NexusClient\instances.json
 *
 * Every change is saved straight away.
 */
public final class InstanceStore {

    /** The shape of instances.json. */
    private record SavedInstances(List<Instance> instances) {}

    // "Pretty printing" makes the file readable if you open it in a text editor.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    /** What an exported instance contains: its setup, but not worlds, logs or screenshots. */
    private static final Set<String> SETUP_FILES = Set.of("mods", "config", "resourcepacks", "shaderpacks",
            "options.txt", "optionsof.txt", "optionsshaders.txt", "servers.dat");

    /** Setup codes start with this, so Nexus can recognise them. */
    private static final String CODE_PREFIX = "NX1-";

    private final Path file = NexusPaths.root().resolve("instances.json");
    private final ObservableList<Instance> instances = FXCollections.observableArrayList();

    public InstanceStore() {
        load();
    }

    /** All instances. The list tells anyone listening (like the Instances panel) when it changes. */
    public ObservableList<Instance> instances() {
        return instances;
    }

    // ================================================================== simple changes

    /** Makes a new instance (with no version yet), saves, and returns it. */
    public Instance add(String name) {
        Instance instance = Instance.create(name);
        instances.add(instance);
        save();
        return instance;
    }

    /** Adds an instance that was set up elsewhere (e.g. a modpack whose files are already in place). */
    public Instance addExisting(Instance instance) {
        onUiThread(() -> {
            instances.add(instance);
            save();
        });
        return instance;
    }

    /** Saves a changed instance (matched by its id) and returns it. */
    public Instance update(Instance updated) {
        // Safe to call from a background thread (e.g. a Game Error Scan fix): the list change
        // happens on the UI thread.
        onUiThread(() -> {
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).id().equals(updated.id())) {
                    instances.set(i, updated);
                    save();
                    return;
                }
            }
        });
        return updated;
    }

    /** Changes an instance's version choice, saves, and returns the updated instance. */
    public Instance setVersion(Instance instance, String version) {
        return update(instance.withVersion(version));
    }

    // ================================================================== duplicate / delete

    /** Makes a copy of an instance – including its worlds, mods and settings – named "<name> (copy)". */
    public Instance duplicate(Instance original) throws IOException {
        Instance copy = original.withNewId(original.name() + " (copy)");
        Path from = NexusPaths.instanceDir(original);
        if (Files.isDirectory(from)) {
            copyFolder(from, NexusPaths.instanceDir(copy), Set.of("logs", "crash-reports"));
        }
        onUiThread(() -> {
            instances.add(instances.indexOf(original) + 1, copy);
            save();
        });
        return copy;
    }

    /**
     * Deletes an instance and its folder. The folder goes to the Recycle Bin / Trash when the
     * computer supports it, so it can still be recovered.
     */
    public void delete(Instance instance) throws IOException {
        Path dir = NexusPaths.instanceDir(instance);
        if (Files.isDirectory(dir)) {
            boolean trashed = false;
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                trashed = Desktop.getDesktop().moveToTrash(dir.toFile());
            }
            if (!trashed) {
                deleteFolder(dir);
            }
        }
        onUiThread(() -> {
            instances.removeIf(existing -> existing.id().equals(instance.id()));
            save();
        });
    }

    // ================================================================== export / import files

    /**
     * Saves an instance's setup to a .zip file that can be shared or kept as a backup:
     * its choices (nexus-instance.json) plus mods, config, resource packs, shaders and game settings.
     * Worlds, logs and screenshots are not included.
     */
    public void export(Instance instance, Path zipFile) throws IOException {
        Path dir = NexusPaths.instanceDir(instance);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zip.putNextEntry(new ZipEntry("nexus-instance.json"));
            zip.write(GSON.toJson(instance.withLastPlayed(0)).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (!Files.isDirectory(dir)) {
                return; // never played: just the choices
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path path : (Iterable<Path>) files::iterator) {
                    Path relative = dir.relativize(path);
                    if (relative.getNameCount() == 0 || !SETUP_FILES.contains(relative.getName(0).toString())
                            || Files.isDirectory(path)) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    /** Adds an instance from a .zip made by export(). Returns the new instance. */
    public Instance importZip(Path zipFile) throws IOException {
        Instance imported = null;
        Path tempDir = Files.createTempDirectory("nexus-import");
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("nexus-instance.json")) {
                    Instance saved = GSON.fromJson(new String(zip.readAllBytes(), StandardCharsets.UTF_8), Instance.class);
                    imported = saved.withNewId(saved.name());
                } else if (!entry.isDirectory()) {
                    Path target = tempDir.resolve(entry.getName()).normalize();
                    if (!target.startsWith(tempDir)) {
                        continue; // safety: never write outside the folder
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (JsonParseException e) {
            throw new IOException("This file isn't a Nexus instance.", e);
        }
        if (imported == null) {
            deleteFolder(tempDir);
            throw new IOException("This file isn't a Nexus instance (it has no nexus-instance.json).");
        }
        Path target = NexusPaths.instanceDir(imported);
        Files.createDirectories(target.getParent());
        try {
            Files.move(tempDir, target);
        } catch (IOException e) {
            // Moving a folder fails if the temporary folder is on a different drive: copy instead.
            copyFolder(tempDir, target, Set.of());
            deleteFolder(tempDir);
        }
        Instance added = imported;
        onUiThread(() -> {
            instances.add(added);
            save();
        });
        return imported;
    }

    // ================================================================== setup codes

    /**
     * Makes a setup code for an instance: a line of text that holds its choices (name, version,
     * mod loader, memory, Java options), which a friend can paste into Nexus to copy the setup.
     *
     * The text is squeezed (gzip) and written with safe characters (base64). Build step 13 (online
     * services) will make these codes short by storing the setup on the Nexus server instead.
     */
    public static String setupCode(Instance instance) {
        JsonObject json = new JsonObject();
        json.addProperty("n", instance.name());
        json.addProperty("v", instance.version());
        json.addProperty("l", instance.loaderOrVanilla());
        json.addProperty("lv", instance.loaderVersion());
        json.addProperty("r", instance.ramMb());
        json.addProperty("j", instance.jvmArgs());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (OutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Squeezing text in memory can't fail", e);
        }
    }

    /** Adds an instance from a setup code. Throws IOException if the code isn't valid. */
    public Instance importCode(String code) throws IOException {
        String trimmed = code.trim();
        if (!trimmed.startsWith(CODE_PREFIX)) {
            throw new IOException("That isn't a Nexus setup code (they start with " + CODE_PREFIX + ").");
        }
        JsonObject json;
        try {
            byte[] squeezed = Base64.getUrlDecoder().decode(trimmed.substring(CODE_PREFIX.length()));
            try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(squeezed))) {
                json = JsonParser.parseString(new String(gzip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (IllegalArgumentException | IOException | IllegalStateException | JsonParseException e) {
            throw new IOException("That setup code is damaged or incomplete. Check you copied all of it.", e);
        }
        Instance instance = Instance.create(text(json, "n", "Shared setup"))
                .withVersion(text(json, "v", null))
                .withLoader(text(json, "l", Instance.VANILLA))
                .withRamMb(json.has("r") ? json.get("r").getAsInt() : 0)
                .withJvmArgs(text(json, "j", ""));
        instances.add(instance);
        save();
        return instance;
    }

    private static String text(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    // ================================================================== instances.json

    /** Reads instances.json. The first time (no file yet), creates the starter instance. */
    private void load() {
        if (Files.exists(file)) {
            try {
                SavedInstances saved = GSON.fromJson(Files.readString(file), SavedInstances.class);
                if (saved != null && saved.instances() != null) {
                    instances.setAll(saved.instances());
                    moveOldFolders();
                    return;
                }
            } catch (IOException | JsonParseException e) {
                // The file is damaged. Keep a copy (so nothing is lost for good) and start fresh.
                e.printStackTrace();
                try {
                    Files.move(file, file.resolveSibling("instances.json.broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // nothing more we can do
                }
            }
        }
        // First run: one starter instance that always plays the newest release.
        // (The player can change its version, and makes any other instances with the + button.)
        instances.setAll(Instance.create("Latest Release").withVersion(Instance.LATEST));
        save();
    }

    /**
     * Instance folders used to be named after the instance; now they're named after its id (so
     * renaming is safe). Moves any old-style folder to its new name.
     */
    private void moveOldFolders() {
        for (Instance instance : instances) {
            Path oldDir = NexusPaths.instances().resolve(NexusPaths.safeFileName(instance.name()));
            Path newDir = NexusPaths.instanceDir(instance);
            if (Files.isDirectory(oldDir) && !Files.exists(newDir)) {
                try {
                    Files.move(oldDir, newDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Writes all instances to instances.json. It writes to a temporary file first and then swaps
     * it in, so the real file is never left half-written if the computer turns off mid-save.
     */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("instances.json.tmp");
            Files.writeString(temp, GSON.toJson(new SavedInstances(List.copyOf(instances))));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace(); // not fatal: the launcher keeps working, it just didn't save
        }
    }

    // ================================================================== folder helpers

    /**
     * The instance list is shown on screen, and JavaFX only allows on-screen things to change on the
     * UI thread. Slow jobs (copying, zipping) run in the background, so they hand the list change
     * over to the UI thread with this.
     */
    private static void onUiThread(Runnable change) {
        if (Platform.isFxApplicationThread()) {
            change.run();
        } else {
            Platform.runLater(change);
        }
    }

    /** Copies a folder and everything in it, skipping the named top-level folders. */
    private static void copyFolder(Path from, Path to, Set<String> skip) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                Path relative = from.relativize(path);
                if (relative.getNameCount() > 0 && skip.contains(relative.getName(0).toString())) {
                    continue;
                }
                Path target = to.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Deletes a folder and everything in it. */
    static void deleteFolder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
