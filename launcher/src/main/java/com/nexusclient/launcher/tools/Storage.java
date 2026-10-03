package com.nexusclient.launcher.tools;

import com.nexusclient.launcher.game.NexusPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Storage – how much space Nexus uses, and safe clean-ups (the project brief: storage cleaner shows how much
 * space Nexus uses and deletes old logs, versions and backups – always asking first).
 *
 * The launcher always shows what a clean-up will delete (and how big it is) before running it.
 */
public final class Storage {

    /** One area of the Nexus folder and its size. */
    public record Area(String name, String explanation, Path path, long bytes) {}

    /** Files a clean-up would delete, and how much space that frees. */
    public record Cleanup(String name, List<Path> files, long bytes) {}

    private static final long WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private Storage() {}

    /** The size of each part of the Nexus folder. Can take a few seconds – run in the background. */
    public static List<Area> measure() {
        Path root = NexusPaths.root();
        List<Area> areas = new ArrayList<>();
        areas.add(area("Instances", "Your worlds, mods, packs and settings", root.resolve("instances")));
        areas.add(area("Minecraft versions", "The game files for each version", root.resolve("versions")));
        areas.add(area("Libraries", "Code packages the game and mod loaders need (shared)", root.resolve("libraries")));
        areas.add(area("Sounds & textures", "Game assets (shared between versions)", root.resolve("assets")));
        areas.add(area("Java", "The Java versions Nexus downloaded", root.resolve("runtimes")));
        areas.add(area("World backups", "Copies of your singleplayer worlds", root.resolve("backups")));
        areas.add(area("Natives", "Unpacked system files (recreated when needed)", root.resolve("natives")));
        areas.sort(Comparator.comparingLong(Area::bytes).reversed());
        return areas;
    }

    /** Log and crash-report files older than a week, in every instance. */
    public static Cleanup oldLogs() {
        List<Path> files = new ArrayList<>();
        Path instances = NexusPaths.instances();
        if (Files.isDirectory(instances)) {
            try (Stream<Path> all = Files.walk(instances, 3)) {
                long cutoff = System.currentTimeMillis() - WEEK_MILLIS;
                all.filter(Files::isRegularFile)
                        .filter(path -> {
                            String parent = path.getParent().getFileName().toString();
                            return parent.equals("logs") || parent.equals("crash-reports");
                        })
                        .filter(path -> path.toFile().lastModified() < cutoff)
                        .forEach(files::add);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cleanup("Old logs and crash reports (older than a week)", files);
    }

    /** Old world backups: all but the newest one of each world. */
    public static Cleanup oldBackups() {
        List<Path> files = new ArrayList<>();
        Path backups = NexusPaths.root().resolve("backups");
        if (Files.isDirectory(backups)) {
            try (Stream<Path> worlds = Files.walk(backups, 2)) {
                for (Path world : worlds.filter(path -> backups.relativize(path).getNameCount() == 2
                        && Files.isDirectory(path)).toList()) {
                    try (Stream<Path> zips = Files.list(world)) {
                        List<Path> sorted = zips.filter(path -> path.toString().endsWith(".zip"))
                                .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
                                .toList();
                        files.addAll(sorted.subList(Math.min(1, sorted.size()), sorted.size()));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cleanup("Old world backups (keeps the newest of each world)", files);
    }

    /** The unpacked natives folders – Nexus recreates them automatically at the next launch. */
    public static Cleanup natives() {
        List<Path> files = new ArrayList<>();
        Path natives = NexusPaths.root().resolve("natives");
        if (Files.isDirectory(natives)) {
            try (Stream<Path> all = Files.walk(natives)) {
                all.filter(Files::isRegularFile).forEach(files::add);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cleanup("Unpacked system files (recreated automatically)", files);
    }

    /** Deletes a clean-up's files. Returns how many bytes were freed. */
    public static long run(Cleanup cleanup) {
        long freed = 0;
        for (Path file : cleanup.files()) {
            try {
                long size = Files.size(file);
                Files.deleteIfExists(file);
                freed += size;
            } catch (IOException e) {
                e.printStackTrace(); // e.g. a log the game is still writing – skip it
            }
        }
        return freed;
    }

    /** 1536 → "1.5 KB", 3_500_000_000 → "3.3 GB". */
    public static String format(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format(Locale.ROOT, "%.0f MB", bytes / (double) (1L << 20));
        if (bytes >= 1L << 10) return String.format(Locale.ROOT, "%.0f KB", bytes / (double) (1L << 10));
        return bytes + " bytes";
    }

    private static Area area(String name, String explanation, Path path) {
        return new Area(name, explanation, path, size(path));
    }

    private static Cleanup cleanup(String name, List<Path> files) {
        long bytes = 0;
        for (Path file : files) {
            bytes += file.toFile().length();
        }
        return new Cleanup(name, files, bytes);
    }

    /** The total size of a folder (0 if it doesn't exist). */
    public static long size(Path path) {
        if (!Files.exists(path)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
