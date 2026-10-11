package com.nexusclient.launcher.tools;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Clips – the video clips saved in game with the Clip Recorder (F8), for Tools → Clips.
 *
 * The recorder saves them as <instance>/clips/Nexus-clip-<date>.mp4. Small preview pictures
 * ("thumbnails") are made with FFmpeg – the same program the recorder uses (Nexus downloads it the first
 * time a clip is recorded) – and kept in <instance>/clips/.thumbs/ so they're only made once.
 */
public final class Clips {

    /** One clip: which instance it belongs to, the file, when it was saved and its size. */
    public record Clip(Instance instance, Path file, long time, long bytes) {}

    private Clips() {}

    /** Every instance's clips, newest first. */
    public static List<Clip> all(List<Instance> instances) throws IOException {
        List<Clip> clips = new ArrayList<>();
        for (Instance instance : instances) {
            Path folder = NexusPaths.instanceDir(instance).resolve("clips");
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4") && Files.isRegularFile(file)) {
                        clips.add(new Clip(instance, file, file.toFile().lastModified(), Files.size(file)));
                    }
                }
            }
        }
        clips.sort(Comparator.comparingLong(Clip::time).reversed());
        return clips;
    }

    /**
     * The clip's preview picture, made the first time it's needed (a frame from 1 second in).
     * Returns null if FFmpeg isn't there yet or the picture couldn't be made.
     */
    public static Path thumbnail(Clip clip) {
        Path thumbs = clip.file().getParent().resolve(".thumbs");
        Path thumb = thumbs.resolve(clip.file().getFileName() + ".jpg");
        if (Files.exists(thumb)) {
            return thumb;
        }
        File ffmpeg = ffmpeg();
        if (ffmpeg == null) {
            return null;
        }
        try {
            Files.createDirectories(thumbs);
            Process process = new ProcessBuilder(ffmpeg.getAbsolutePath(), "-y", "-loglevel", "error",
                    "-ss", "1", "-i", clip.file().toString(), "-frames:v", "1", "-vf", "scale=464:-2", thumb.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 && Files.exists(thumb) ? thumb : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Deletes a clip and its preview picture. */
    public static void delete(Clip clip) throws IOException {
        Files.deleteIfExists(clip.file());
        Files.deleteIfExists(clip.file().getParent().resolve(".thumbs").resolve(clip.file().getFileName() + ".jpg"));
    }

    /** FFmpeg: Nexus's own copy (tools/ffmpeg), or one installed on the PC. Null if there's none. */
    static File ffmpeg() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String name = windows ? "ffmpeg.exe" : "ffmpeg";
        File own = NexusPaths.tools().resolve("ffmpeg").resolve(name).toFile();
        if (own.isFile()) {
            return own;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File candidate = new File(dir, name);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        for (String dir : new String[] {"/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"}) {
            File candidate = new File(dir, name);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }
}
