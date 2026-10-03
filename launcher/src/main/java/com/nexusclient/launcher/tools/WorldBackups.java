package com.nexusclient.launcher.tools;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.settings.SettingsStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * WorldBackups – copies of singleplayer worlds, so a broken or griefed world can be put back
 * (the project brief: Tools → world backups, with one-click restore).
 *
 * Each backup is a .zip of the world's folder, saved in:
 *   backups/<instance id>/<world name>/<date-time>.zip
 *
 * Automatic backups happen after the game closes, for every world that was played in that session.
 * Only the newest few are kept (Settings: backupsToKeep).
 */
public final class WorldBackups {

    /** One saved backup. */
    public record Backup(String world, Path file, long sizeBytes, long createdMillis) {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private WorldBackups() {}

    /** The instance's singleplayer worlds (folder names). */
    public static List<String> worlds(Instance instance) throws IOException {
        Path saves = NexusPaths.instanceDir(instance).resolve("saves");
        List<String> worlds = new ArrayList<>();
        if (!Files.isDirectory(saves)) {
            return worlds;
        }
        try (Stream<Path> dirs = Files.list(saves)) {
            dirs.filter(dir -> Files.exists(dir.resolve("level.dat")))
                    .sorted()
                    .forEach(dir -> worlds.add(dir.getFileName().toString()));
        }
        return worlds;
    }

    /** All backups of one instance, newest first. */
    public static List<Backup> list(Instance instance) throws IOException {
        Path dir = backupDir(instance);
        List<Backup> backups = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return backups;
        }
        try (Stream<Path> files = Files.walk(dir, 2)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".zip")).toList()) {
                backups.add(new Backup(file.getParent().getFileName().toString(), file, Files.size(file),
                        Files.getLastModifiedTime(file).toMillis()));
            }
        }
        backups.sort(Comparator.comparingLong(Backup::createdMillis).reversed());
        return backups;
    }

    /** Zips a world into a new backup. */
    public static Backup backUp(Instance instance, String world) throws IOException {
        Path source = NexusPaths.instanceDir(instance).resolve("saves").resolve(world);
        Path target = backupDir(instance).resolve(world).resolve(LocalDateTime.now().format(STAMP) + ".zip");
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target));
             Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().equals("session.lock")) {
                    continue; // the game locks this file while the world is open
                }
                zip.putNextEntry(new ZipEntry(source.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return new Backup(world, target, Files.size(target), System.currentTimeMillis());
    }

    /**
     * Puts a backup back. The world as it is right now is backed up first, so restoring can
     * itself be undone.
     */
    public static void restore(Instance instance, Backup backup) throws IOException {
        Path world = NexusPaths.instanceDir(instance).resolve("saves").resolve(backup.world());
        if (Files.exists(world.resolve("level.dat"))) {
            backUp(instance, backup.world()); // safety copy of the current world
            deleteFolder(world);
        }
        Files.createDirectories(world);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(backup.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = world.resolve(entry.getName()).normalize();
                if (!target.startsWith(world) || entry.isDirectory()) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void delete(Backup backup) throws IOException {
        Files.deleteIfExists(backup.file());
    }

    /**
     * Automatic backups after a play session: backs up every world changed since the game started,
     * then deletes the oldest backups beyond the "keep" setting. Problems are printed, not thrown –
     * a failed backup must never stop the player.
     */
    public static void autoBackUp(Instance instance, long sessionStartMillis) {
        if (!SettingsStore.get().autoBackups) {
            return;
        }
        try {
            for (String world : worlds(instance)) {
                Path levelDat = NexusPaths.instanceDir(instance).resolve("saves").resolve(world).resolve("level.dat");
                if (Files.getLastModifiedTime(levelDat).toMillis() >= sessionStartMillis) {
                    backUp(instance, world);
                    trim(instance, world, SettingsStore.get().backupsToKeep);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Keeps only the newest "keep" backups of a world. */
    private static void trim(Instance instance, String world, int keep) throws IOException {
        List<Backup> backups = list(instance).stream().filter(backup -> backup.world().equals(world)).toList();
        for (int i = Math.max(1, keep); i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i).file());
        }
    }

    private static Path backupDir(Instance instance) {
        return NexusPaths.root().resolve("backups").resolve(instance.id());
    }

    static void deleteFolder(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
