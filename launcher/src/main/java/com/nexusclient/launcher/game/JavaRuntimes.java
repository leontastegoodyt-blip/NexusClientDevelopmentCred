package com.nexusclient.launcher.game;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * JavaRuntimes – downloads the exact Java each Minecraft version needs.
 *
 * Each version's JSON names a Java "component", e.g. "jre-legacy" (Java 8, for 1.8.9) or
 * "java-runtime-epsilon" (Java 25, for the newest versions). Mojang hosts those Javas itself –
 * the official launcher uses them too – so we download from the same place:
 *   1. Mojang's list of all its Javas, for every kind of computer
 *   2. the file list for the one we need
 *   3. every file in it, into runtimes/<component>/
 * Players never have to install Java themselves.
 */
public final class JavaRuntimes {

    /** Mojang's list of Java runtimes (the same link the official launcher uses). */
    private static final String RUNTIME_LIST_URL = "https://launchermeta.mojang.com/v1/products/java-runtime/"
            + "2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    // The shapes of Mojang's JSON files, for Gson.
    private record Ref(String sha1, long size, String url) {}
    private record RuntimeVersion(String name) {}
    private record RuntimeEntry(Ref manifest, RuntimeVersion version) {}
    private record RuntimeFile(String type, Map<String, Ref> downloads, boolean executable, String target) {}
    private record RuntimeManifest(Map<String, RuntimeFile> files) {}

    private static final Gson GSON = new Gson();

    private JavaRuntimes() {}

    /**
     * Makes sure a Java is downloaded, and returns the path to its "java" program.
     *
     * @param component    Mojang's name for it, e.g. "jre-legacy"
     * @param majorVersion its Java version number, e.g. 8 – only used in progress messages
     */
    public static Path ensure(String component, int majorVersion, Downloader downloader,
                              ProgressListener progress) throws IOException, InterruptedException {
        OperatingSystem os = OperatingSystem.current();
        Path dir = NexusPaths.runtimes().resolve(component);
        progress.update("Checking Java " + majorVersion + "…", -1);

        RuntimeManifest manifest;
        try {
            manifest = fetchManifest(component, os, downloader);
        } catch (IOException e) {
            // No internet? If this Java was downloaded before, just use it.
            Path existing = findJava(dir, os);
            if (existing != null) {
                return existing;
            }
            throw e;
        }

        // Go through the file list: make the folders, and collect the files to download.
        List<Downloader.Job> jobs = new ArrayList<>();
        String javaProgram = null;
        for (Map.Entry<String, RuntimeFile> entry : manifest.files().entrySet()) {
            String path = entry.getKey();
            RuntimeFile file = entry.getValue();
            Path target = dir.resolve(path);
            if ("directory".equals(file.type())) {
                Files.createDirectories(target);
            } else if ("file".equals(file.type())) {
                Ref raw = file.downloads().get("raw");
                jobs.add(new Downloader.Job(raw.url(), target, raw.sha1(), raw.size()));
                // Remember where the java program is. If there's more than one, the shortest path
                // is the main one.
                if (isJavaProgram(path, os) && (javaProgram == null || path.length() < javaProgram.length())) {
                    javaProgram = path;
                }
            }
        }

        downloader.downloadAll(jobs, "Downloading Java " + majorVersion, progress);

        // Mac and Linux need programs to be marked "executable", and use shortcut files ("links").
        if (os != OperatingSystem.WINDOWS) {
            for (Map.Entry<String, RuntimeFile> entry : manifest.files().entrySet()) {
                RuntimeFile file = entry.getValue();
                Path target = dir.resolve(entry.getKey());
                if ("file".equals(file.type()) && file.executable()) {
                    target.toFile().setExecutable(true);
                } else if ("link".equals(file.type()) && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target.getParent());
                    Files.createSymbolicLink(target, Path.of(file.target()));
                }
            }
        }

        if (javaProgram == null) {
            throw new IOException("Mojang's Java " + majorVersion + " download didn't contain a java program");
        }
        return dir.resolve(javaProgram);
    }

    /** Gets the file list for one Java, for this kind of computer. */
    private static RuntimeManifest fetchManifest(String component, OperatingSystem os, Downloader downloader)
            throws IOException, InterruptedException {
        // The list is shaped: { platform: { component: [ entry ] } }
        Type listType = new TypeToken<Map<String, Map<String, List<RuntimeEntry>>>>() {}.getType();
        Map<String, Map<String, List<RuntimeEntry>>> all =
                GSON.fromJson(downloader.fetchString(RUNTIME_LIST_URL), listType);

        for (String platform : os.javaRuntimePlatforms()) {
            List<RuntimeEntry> entries = all.getOrDefault(platform, Map.of()).get(component);
            if (entries != null && !entries.isEmpty()) {
                String manifestJson = downloader.fetchString(entries.get(0).manifest().url());
                return GSON.fromJson(manifestJson, RuntimeManifest.class);
            }
        }
        throw new IOException("Mojang doesn't provide Java '" + component + "' for this computer");
    }

    /**
     * The java program to start Minecraft with. On Windows that's "javaw.exe" – the version of
     * Java that doesn't open a black command-prompt window next to the game.
     */
    private static boolean isJavaProgram(String path, OperatingSystem os) {
        return os == OperatingSystem.WINDOWS ? path.endsWith("bin/javaw.exe") : path.endsWith("bin/java");
    }

    /** Looks for a java program in an already-downloaded Java folder. Returns null if there isn't one. */
    private static Path findJava(Path dir, OperatingSystem os) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files
                    .filter(path -> isJavaProgram(dir.relativize(path).toString().replace('\\', '/'), os))
                    .min(Comparator.comparingInt(path -> path.toString().length()))
                    .orElse(null);
        }
    }
}
