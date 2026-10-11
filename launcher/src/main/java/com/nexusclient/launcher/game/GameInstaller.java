package com.nexusclient.launcher.game;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.minecraft.MinecraftVersion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * GameInstaller – downloads everything one Minecraft version needs (except Java, see JavaRuntimes):
 *
 *   1. the version's JSON file (the "recipe" that lists everything below)
 *   2. the game itself (client .jar) and its libraries
 *   3. "natives": computer-specific files (.dll on Windows) that old versions need unpacked
 *   4. the logging settings file (fixes the Log4Shell security hole in old versions)
 *   5. the assets: sounds, languages and textures
 *
 * Files are always downloaded from Mojang – Nexus never ships Minecraft's own files (the project brief §8).
 */
public final class GameInstaller {

    /** Where the sounds/textures are downloaded from. */
    private static final String ASSETS_URL = "https://resources.download.minecraft.net/";

    private static final Gson GSON = new Gson();

    /**
     * Everything needed to start an installed version.
     *
     * @param version         the version's JSON
     * @param classpath       every .jar the game needs, in order (libraries, then the game)
     * @param nativesDir      where the computer-specific files are
     * @param assetsRoot      the shared assets folder
     * @param gameAssetsDir   the assets folder very old versions look in
     * @param logConfig       the downloaded logging settings file (null if the version has none)
     */
    public record InstalledGame(VersionJson version, List<Path> classpath, Path nativesDir,
                                Path assetsRoot, Path gameAssetsDir, Path logConfig) {}

    // The shape of a version's assets list, for Gson.
    private record AssetObject(String hash, long size) {}
    private record AssetIndex(Map<String, AssetObject> objects, boolean virtual,
                              @SerializedName("map_to_resources") boolean mapToResources) {}

    private GameInstaller() {}

    /**
     * Downloads and prepares a version.
     *
     * @param loader        the mod loader: Instance.VANILLA, FABRIC or FORGE
     * @param loaderVersion a specific loader version, or null for the newest stable one
     * @param gameDir       the instance's game folder (only very old versions put files in it)
     */
    public static InstalledGame install(MinecraftVersion minecraftVersion, String loader, String loaderVersion,
                                        Path gameDir, Downloader downloader, ProgressListener progress)
            throws IOException, InterruptedException {
        String id = minecraftVersion.id();

        // --- 1. The version's JSON ---
        progress.update("Getting Minecraft " + id + " details…", -1);
        Path versionDir = NexusPaths.versions().resolve(id);
        Path jsonPath = versionDir.resolve(id + ".json");
        downloader.download(new Downloader.Job(minecraftVersion.url(), jsonPath, minecraftVersion.sha1(), 0));
        VersionJson version = GSON.fromJson(Files.readString(jsonPath), VersionJson.class);

        // --- Mod loader: combine its profile with the Minecraft version ---
        if (Instance.FABRIC.equals(loader)) {
            version = ModLoaders.merge(version, ModLoaders.fabricProfile(id, loaderVersion, downloader, progress));
        } else if (Instance.FORGE.equals(loader)) {
            // Forge's installer runs on the same Java the game needs.
            VersionJson.JavaVersion javaVersion = version.javaVersion();
            Path java = JavaRuntimes.ensure(javaVersion != null ? javaVersion.component() : "jre-legacy",
                    javaVersion != null ? javaVersion.majorVersion() : 8, downloader, progress);
            version = ModLoaders.merge(version, ModLoaders.forgeProfile(id, loaderVersion, java, downloader, progress));
        }

        // --- 2. The game and its libraries (and 3. collect the natives files) ---
        List<Downloader.Job> jobs = new ArrayList<>();
        List<Path> classpath = new ArrayList<>();
        List<NativesJar> nativesJars = new ArrayList<>();

        for (VersionJson.Library library : version.libraries()) {
            if (!Rules.allows(library.rules())) {
                continue; // not needed on this computer
            }
            if (library.downloads() == null) {
                // Mod loader libraries: a library server address plus a name, instead of a download link.
                if (library.url() != null) {
                    String server = library.url().endsWith("/") ? library.url() : library.url() + "/";
                    if (library.natives() != null) {
                        // A "natives only" library: just the file for this computer, to be unpacked.
                        String classifier = library.natives().get(OperatingSystem.current().mojangName());
                        if (classifier != null) {
                            classifier = classifier.replace("${arch}", OperatingSystem.is64Bit() ? "64" : "32");
                            String mavenPath = ModLoaders.mavenPath(library.name() + ":" + classifier);
                            Path path = NexusPaths.libraries().resolve(mavenPath);
                            jobs.add(new Downloader.Job(server + mavenPath, path, null, 0));
                            List<String> exclude = library.extract() != null ? library.extract().exclude() : List.of();
                            nativesJars.add(new NativesJar(path, exclude));
                        }
                    } else {
                        String mavenPath = ModLoaders.mavenPath(library.name());
                        Path path = NexusPaths.libraries().resolve(mavenPath);
                        jobs.add(new Downloader.Job(server + mavenPath, path, library.sha1(), library.size()));
                        if (!classpath.contains(path)) {
                            classpath.add(path);
                        }
                    }
                }
                continue;
            }
            VersionJson.Artifact artifact = library.downloads().artifact();
            if (artifact != null) {
                Path path = NexusPaths.libraries().resolve(artifact.path());
                jobs.add(new Downloader.Job(artifact.url(), path, artifact.sha1(), artifact.size()));
                if (!classpath.contains(path)) {
                    classpath.add(path);
                }
            }
            // Old versions: computer-specific files come in a separate "classifier" download.
            if (library.natives() != null && library.downloads().classifiers() != null) {
                String classifier = library.natives().get(OperatingSystem.current().mojangName());
                if (classifier != null) {
                    classifier = classifier.replace("${arch}", OperatingSystem.is64Bit() ? "64" : "32");
                    VersionJson.Artifact natives = library.downloads().classifiers().get(classifier);
                    if (natives != null) {
                        Path path = NexusPaths.libraries().resolve(natives.path());
                        jobs.add(new Downloader.Job(natives.url(), path, natives.sha1(), natives.size()));
                        List<String> exclude = library.extract() != null ? library.extract().exclude() : List.of();
                        nativesJars.add(new NativesJar(path, exclude));
                    }
                }
            }
        }

        // The game itself goes last on the classpath.
        VersionJson.Artifact client = version.downloads().get("client");
        Path clientJar = versionDir.resolve(id + ".jar");
        jobs.add(new Downloader.Job(client.url(), clientJar, client.sha1(), client.size()));
        // Forge skips the plain game file only if it's named after the Forge version (its
        // "-DignoreList=…,${version_name}.jar"), so Forge gets a copy with that name (made below).
        Path forgeJar = Instance.FORGE.equals(loader)
                ? NexusPaths.versions().resolve(version.id()).resolve(version.id() + ".jar") : null;
        classpath.add(forgeJar != null ? forgeJar : clientJar);

        // --- 4. Logging settings ---
        Path logConfig = null;
        if (version.logging() != null && version.logging().client() != null) {
            VersionJson.LoggingFile file = version.logging().client().file();
            logConfig = NexusPaths.assets().resolve("log_configs").resolve(file.id());
            jobs.add(new Downloader.Job(file.url(), logConfig, file.sha1(), file.size()));
        }

        downloader.downloadAll(jobs, "Downloading Minecraft " + id, progress);
        if (forgeJar != null && (!Files.exists(forgeJar) || Files.size(forgeJar) != Files.size(clientJar))) {
            Files.createDirectories(forgeJar.getParent());
            Files.copy(clientJar, forgeJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // --- 3. Unpack the natives ---
        // Each setup gets its own folder (e.g. "1.8.9" or "fabric-loader-0.19.3-1.8.9"), because a mod
        // loader can bring its own versions of these files.
        Path nativesDir = NexusPaths.natives(version.id()).toAbsolutePath().normalize();
        Files.createDirectories(nativesDir);
        for (NativesJar jar : nativesJars) {
            extract(jar, nativesDir);
        }

        // --- 5. Assets ---
        Path gameAssetsDir = installAssets(version.assetIndex(), gameDir, downloader, progress);

        return new InstalledGame(version, classpath, nativesDir, NexusPaths.assets(), gameAssetsDir, logConfig);
    }

    /**
     * Downloads the version's sounds, languages and textures.
     * Each file is saved under its fingerprint (e.g. objects/ab/ab12cd...), so files shared between
     * versions are only downloaded once.
     *
     * @return the folder very old versions read assets from (for newer versions: the assets folder)
     */
    private static Path installAssets(VersionJson.AssetIndexRef ref, Path gameDir, Downloader downloader,
                                      ProgressListener progress) throws IOException, InterruptedException {
        progress.update("Getting the sounds and textures list…", -1);
        Path assets = NexusPaths.assets();
        Path indexPath = assets.resolve("indexes").resolve(ref.id() + ".json");
        downloader.download(new Downloader.Job(ref.url(), indexPath, ref.sha1(), ref.size()));
        AssetIndex index = GSON.fromJson(Files.readString(indexPath), AssetIndex.class);

        List<Downloader.Job> jobs = new ArrayList<>();
        for (AssetObject object : index.objects().values()) {
            String hashPath = object.hash().substring(0, 2) + "/" + object.hash();
            jobs.add(new Downloader.Job(ASSETS_URL + hashPath,
                    assets.resolve("objects").resolve(hashPath), object.hash(), object.size()));
        }
        downloader.downloadAll(jobs, "Downloading sounds & textures", progress);

        // Very old versions (before 1.7.3) want the files under their real names, not fingerprints.
        if (index.virtual() || index.mapToResources()) {
            Path copyTo = index.mapToResources()
                    ? gameDir.resolve("resources")
                    : assets.resolve("virtual").resolve(ref.id());
            for (Map.Entry<String, AssetObject> entry : index.objects().entrySet()) {
                String hash = entry.getValue().hash();
                Path source = assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                Path target = copyTo.resolve(entry.getKey());
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
            return copyTo;
        }
        return assets;
    }

    /** A natives .jar and the paths inside it that should NOT be unpacked. */
    private record NativesJar(Path path, List<String> exclude) {}

    /** Unpacks a natives .jar (which is really a .zip file) into the natives folder. */
    private static void extract(NativesJar jar, Path nativesDir) throws IOException {
        try (ZipFile zip = new ZipFile(jar.path().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || isExcluded(entry.getName(), jar.exclude())) {
                    continue;
                }
                Path target = nativesDir.resolve(entry.getName()).normalize();
                // Safety: never write outside the natives folder, even if a zip entry says "../"
                if (!target.startsWith(nativesDir) || Files.exists(target)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target);
                }
            }
        }
    }

    private static boolean isExcluded(String name, List<String> exclude) {
        for (String prefix : exclude) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
