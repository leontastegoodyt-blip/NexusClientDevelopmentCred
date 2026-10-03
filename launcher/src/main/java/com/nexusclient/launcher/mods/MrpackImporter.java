package com.nexusclient.launcher.mods;

import com.google.gson.Gson;
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
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * MrpackImporter – installs a Modrinth modpack (a ".mrpack" file) as a new instance.
 *
 * An .mrpack is a zip file containing:
 *   - modrinth.index.json: the pack's name, which Minecraft version and mod loader it needs, and a
 *     list of files to download (mostly mods, each with a download link and fingerprint)
 *   - "overrides" (and "client-overrides") folders: extra files copied straight into the instance,
 *     like config files
 */
public final class MrpackImporter {

    // The shape of modrinth.index.json.
    private record Env(String client, String server) {}
    private record PackFile(String path, Map<String, String> hashes, List<String> downloads, long fileSize, Env env) {}
    private record PackIndex(String name, String versionId, List<PackFile> files, Map<String, String> dependencies) {}

    private static final Gson GSON = new Gson();

    private MrpackImporter() {}

    /**
     * Reads the modpack, downloads its files into a new instance folder, and returns the new
     * instance (not yet added to the instance list – the caller does that).
     */
    public static Instance importPack(Path mrpack, ProgressListener progress) throws IOException, InterruptedException {
        try (ZipFile zip = new ZipFile(mrpack.toFile())) {
            ZipEntry indexEntry = zip.getEntry("modrinth.index.json");
            if (indexEntry == null) {
                throw new IOException("This isn't a Modrinth modpack (it has no modrinth.index.json).");
            }
            PackIndex pack;
            try (InputStream in = zip.getInputStream(indexEntry)) {
                pack = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), PackIndex.class);
            }

            // --- Which Minecraft and mod loader it needs ---
            Map<String, String> needs = pack.dependencies() == null ? Map.of() : pack.dependencies();
            String minecraft = needs.get("minecraft");
            if (minecraft == null) {
                throw new IOException("This modpack doesn't say which Minecraft version it's for.");
            }
            for (String unsupported : List.of("forge", "neoforge", "quilt-loader")) {
                if (needs.containsKey(unsupported)) {
                    throw new IOException("This modpack needs " + unsupported.replace("-loader", "")
                            + ", which Nexus doesn't support yet. Look for a Fabric version of the pack.");
                }
            }
            Instance instance = Instance.create(pack.name() == null ? "Modpack" : pack.name()).withVersion(minecraft);
            if (needs.containsKey("fabric-loader")) {
                instance = instance.withLoader(Instance.FABRIC).withLoaderVersion(needs.get("fabric-loader"));
            }
            Path dir = NexusPaths.instanceDir(instance);

            // --- Download its files ---
            List<Downloader.Job> jobs = new ArrayList<>();
            for (PackFile file : pack.files()) {
                if (file.env() != null && "unsupported".equals(file.env().client())) {
                    continue; // server-only file
                }
                Path target = safePath(dir, file.path());
                if (file.downloads() == null || file.downloads().isEmpty()) {
                    continue;
                }
                String sha1 = file.hashes() == null ? null : file.hashes().get("sha1");
                jobs.add(new Downloader.Job(file.downloads().get(0), target, sha1, file.fileSize()));
            }
            try (Downloader downloader = new Downloader()) {
                downloader.downloadAll(jobs, "Downloading " + instance.name(), progress);
            }

            // --- Copy the "overrides" folders ---
            progress.update("Copying the modpack's settings…", -1);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                String relative = null;
                if (name.startsWith("overrides/")) {
                    relative = name.substring("overrides/".length());
                } else if (name.startsWith("client-overrides/")) {
                    relative = name.substring("client-overrides/".length());
                }
                if (relative == null || relative.isEmpty() || entry.isDirectory()) {
                    continue;
                }
                Path target = safePath(dir, relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return instance;
        }
    }

    /** A path inside the instance folder. Refuses paths that try to escape it (e.g. "../../"). */
    private static Path safePath(Path dir, String relative) throws IOException {
        Path target = dir.resolve(relative).normalize();
        if (!target.startsWith(dir.normalize())) {
            throw new IOException("This modpack tries to put a file outside its folder, so it wasn't installed.");
        }
        return target;
    }
}
