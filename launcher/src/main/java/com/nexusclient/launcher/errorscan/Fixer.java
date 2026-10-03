package com.nexusclient.launcher.errorscan;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.mods.ContentType;
import com.nexusclient.launcher.mods.ModManager;
import com.nexusclient.launcher.mods.ModrinthApi;
import com.nexusclient.launcher.settings.SettingsStore;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Fixer – carries out the Game Error Scan's fixes, and undoes them (the project brief: backs up files before
 * any fix, with an "Undo fix" button).
 *
 * Fixes never delete the player's files: anything removed is MOVED into a backup folder
 * (fix-backups/<time>/…), and every change is recorded as undo steps (see ScanHistory.Step).
 * Run fixes on a background thread – some download mods.
 */
public final class Fixer {

    /** What a fix did: a description for the player, and how to undo it. */
    public record Result(String description, List<ScanHistory.Step> undo) {}

    /** Mod ids whose Modrinth page has a different name. */
    private static final Map<String, String> MODRINTH_NAMES = Map.of(
            "fabric", "fabric-api",
            "cloth-config2", "cloth-config",
            "cloth_config", "cloth-config");

    private Fixer() {}

    /**
     * Applies the fix for a diagnosis.
     *
     * @param versionId the Minecraft version the instance plays
     */
    public static Result apply(Diagnosis diagnosis, Instance instance, String versionId, InstanceStore store)
            throws IOException, InterruptedException {
        Path backups = NexusPaths.root().resolve("fix-backups").resolve(String.valueOf(System.currentTimeMillis()));
        List<ScanHistory.Step> undo = new ArrayList<>();
        String arg = diagnosis.fixArg();
        return switch (diagnosis.fix()) {
            case "RAISE_RAM", "LOWER_RAM" -> {
                int current = currentRam(instance, versionId);
                long totalMb = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                        .getTotalMemorySize() / (1024 * 1024);
                int updated = diagnosis.fix().equals("RAISE_RAM")
                        ? (int) Math.min(current + 1024, Math.max(2048, totalMb - 2048))
                        : Math.max(1024, current - 1024);
                store.update(instance.withRamMb(updated));
                undo.add(new ScanHistory.Step("SET_RAM", instance.id(), String.valueOf(instance.ramMb())));
                yield new Result("Changed \"" + instance.name() + "\"'s memory from " + current + " MB to "
                        + updated + " MB", undo);
            }
            case "INSTALL_DEPENDENCY" -> {
                Path mods = NexusPaths.instanceDir(instance).resolve("mods");
                Set<Path> before = listFiles(mods);
                String project = findOnModrinth(arg, versionId, new ModManager(instance, versionId));
                String title = new ModManager(instance, versionId).install(project, ContentType.MOD, (m, f) -> {});
                for (Path added : listFiles(mods)) {
                    if (!before.contains(added)) {
                        undo.add(new ScanHistory.Step("ADDED", added.toString(), null));
                    }
                }
                yield new Result("Installed \"" + title + "\" (and anything it needs)", undo);
            }
            case "DISABLE_MOD" -> {
                List<Path> jars = jarsFor(instance, arg);
                if (jars.isEmpty()) {
                    throw new IOException("Couldn't find the mod \"" + arg + "\" in this instance's mods folder.");
                }
                for (Path jar : jars) {
                    disable(jar, undo);
                }
                yield new Result("Switched off " + names(jars), undo);
            }
            case "DISABLE_DUPLICATE" -> {
                List<Path> jars = new ArrayList<>(jarsFor(instance, arg));
                jars.sort(Comparator.comparingLong((Path jar) -> jar.toFile().lastModified()).reversed());
                List<Path> older = jars.subList(Math.min(1, jars.size()), jars.size());
                for (Path jar : older) {
                    disable(jar, undo);
                }
                yield new Result("Switched off the older copies: " + names(older), undo);
            }
            case "UPDATE_OR_DISABLE_MOD" -> {
                List<Path> jars = jarsFor(instance, arg);
                if (jars.isEmpty()) {
                    throw new IOException("Couldn't find the mod \"" + arg + "\" in this instance's mods folder.");
                }
                ModManager manager = new ModManager(instance, versionId);
                Map<String, ModrinthApi.Version> updates = manager.checkUpdates(ContentType.MOD);
                ModrinthApi.Version newer = updates.get(jars.get(0).getFileName().toString());
                if (newer == null) {
                    for (Path jar : jars) {
                        disable(jar, undo);
                    }
                    yield new Result("No update was available, so switched off " + names(jars), undo);
                }
                Path mods = jars.get(0).getParent();
                for (Path jar : jars) {
                    moveToBackup(jar, backups, undo); // keep the old version, in case the update is worse
                }
                Set<Path> before = listFiles(mods);
                manager.install(newer.project_id(), ContentType.MOD, (m, f) -> {});
                for (Path added : listFiles(mods)) {
                    if (!before.contains(added)) {
                        undo.add(new ScanHistory.Step("ADDED", added.toString(), null));
                    }
                }
                yield new Result("Updated " + names(jars) + " to " + newer.version_number(), undo);
            }
            case "TURN_OFF_SHADERS" -> {
                ModManager manager = new ModManager(instance, versionId);
                boolean wasOn = manager.shadersEnabled();
                manager.setShaders(false, null);
                undo.add(new ScanHistory.Step("SHADERS", instance.id(), String.valueOf(wasOn)));
                yield new Result("Turned shaders off", undo);
            }
            case "RESET_CONFIG" -> {
                Path dir = NexusPaths.instanceDir(instance);
                Path file = dir.resolve(arg.replace('\\', '/')).normalize();
                if (!file.startsWith(dir) || !Files.exists(file)) {
                    throw new IOException("Couldn't find the settings file \"" + arg + "\".");
                }
                moveToBackup(file, backups, undo);
                yield new Result("Reset \"" + arg + "\" (the old file was kept, in case you want it back)", undo);
            }
            case "REDOWNLOAD_FILES" -> {
                String id = arg != null && NexusPaths.versions().resolve(arg).toFile().isDirectory() ? arg : versionId;
                Path jar = NexusPaths.versions().resolve(id).resolve(id + ".jar");
                if (Files.exists(jar)) {
                    moveToBackup(jar, backups, undo);
                }
                Path natives = NexusPaths.root().resolve("natives");
                if (Files.isDirectory(natives)) {
                    try (Stream<Path> dirs = Files.list(natives)) {
                        for (Path dir : dirs.filter(d -> d.getFileName().toString().equals(id)
                                || d.getFileName().toString().endsWith("-" + id)).toList()) {
                            moveToBackup(dir, backups, undo);
                        }
                    }
                }
                yield new Result("Cleared the Minecraft " + id + " game files – they'll be downloaded fresh on the next launch", undo);
            }
            default -> throw new IOException("There's no automatic fix for this problem.");
        };
    }

    /** Reverses a fix, step by step (last change first). */
    public static void undo(ScanHistory.Entry entry, InstanceStore store) throws IOException {
        List<ScanHistory.Step> steps = new ArrayList<>(entry.undo());
        java.util.Collections.reverse(steps);
        for (ScanHistory.Step step : steps) {
            switch (step.action()) {
                case "MOVE" -> {
                    Path original = Path.of(step.a());
                    Path moved = Path.of(step.b());
                    if (Files.exists(moved)) {
                        Files.createDirectories(original.getParent());
                        Files.move(moved, original, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                case "ADDED" -> Files.deleteIfExists(Path.of(step.a()));
                case "SET_RAM" -> find(store, step.a()).ifPresent(instance ->
                        store.update(instance.withRamMb(Integer.parseInt(step.b()))));
                case "SHADERS" -> {
                    Instance instance = find(store, step.a()).orElse(null);
                    if (instance != null) {
                        new ModManager(instance, "").setShaders(Boolean.parseBoolean(step.b()), null);
                    }
                }
                default -> { }
            }
        }
        ScanHistory.markUndone(entry.id());
    }

    // ------------------------------------------------------------------ helpers

    /** The memory the instance currently gets, in MB. */
    private static int currentRam(Instance instance, String versionId) {
        if (instance.ramMb() > 0) return instance.ramMb();
        if (SettingsStore.get().ramMb > 0) return SettingsStore.get().ramMb;
        return versionId.startsWith("1.8") ? 2048 : 4096;
    }

    /** The mod files (switched on) whose mod id – or file name – matches. */
    private static List<Path> jarsFor(Instance instance, String modId) throws IOException {
        Path mods = NexusPaths.instanceDir(instance).resolve("mods");
        List<Path> jars = new ArrayList<>();
        if (modId == null || !Files.isDirectory(mods)) {
            return jars;
        }
        try (Stream<Path> files = Files.list(mods)) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).toList()) {
                ModInfo info = ModInfo.read(jar);
                boolean matches = info != null ? info.id().equals(modId)
                        : jar.getFileName().toString().toLowerCase().contains(modId.toLowerCase());
                if (matches) {
                    jars.add(jar);
                }
            }
        }
        return jars;
    }

    /** Switches a mod off by renaming it to .jar.disabled (recorded so it can be undone). */
    private static void disable(Path jar, List<ScanHistory.Step> undo) throws IOException {
        Path off = jar.resolveSibling(jar.getFileName() + ".disabled");
        Files.move(jar, off, StandardCopyOption.REPLACE_EXISTING);
        undo.add(new ScanHistory.Step("MOVE", jar.toString(), off.toString()));
    }

    /** Moves a file or folder into the fix's backup folder (recorded so it can be undone). */
    private static void moveToBackup(Path path, Path backups, List<ScanHistory.Step> undo) throws IOException {
        Path target = backups.resolve(NexusPaths.root().relativize(path.toAbsolutePath()).toString());
        Files.createDirectories(target.getParent());
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        undo.add(new ScanHistory.Step("MOVE", path.toString(), target.toString()));
    }

    /** Finds a mod's Modrinth project from its mod id (usually the same name, sometimes not). */
    private static String findOnModrinth(String modId, String versionId, ModManager manager)
            throws IOException, InterruptedException {
        String name = MODRINTH_NAMES.getOrDefault(modId, modId);
        try {
            return ModrinthApi.project(name).id();
        } catch (IOException notFound) {
            List<ModrinthApi.Hit> hits = ModrinthApi.search(modId, "mod", versionId, manager.modLoader(),
                    "relevance", 0, 1).hits();
            if (hits.isEmpty()) {
                throw new IOException("Couldn't find \"" + modId + "\" on Modrinth. You may need to download it yourself.");
            }
            return hits.get(0).project_id();
        }
    }

    private static Set<Path> listFiles(Path folder) throws IOException {
        Set<Path> files = new HashSet<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> list = Files.list(folder)) {
                list.forEach(files::add);
            }
        }
        return files;
    }

    private static java.util.Optional<Instance> find(InstanceStore store, String id) {
        return store.instances().stream().filter(instance -> instance.id().equals(id)).findFirst();
    }

    private static String names(List<Path> jars) {
        return String.join(", ", jars.stream().map(jar -> jar.getFileName().toString()).toList());
    }
}
