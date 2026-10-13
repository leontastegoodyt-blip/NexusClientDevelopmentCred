package com.nexusclient.core.record;

import com.nexusclient.core.module.Mods;
import com.nexusclient.core.platform.GameInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ClipRecorder – "press a key to save the last 30 seconds" (the project brief section 4, Recording).
 *
 * How it works: while the Clip Recorder mod is on, FFmpeg (a separate program, see Ffmpeg.java)
 * records the game into a rolling buffer of 2-second video pieces in clips/.buffer, overwriting
 * the oldest ones. The graphics card's own video encoder does the work (NVIDIA NVENC, AMD AMF or
 * Intel Quick Sync, else the CPU as a last resort), so it barely costs FPS. Saving a clip joins
 * the newest pieces and trims them to the chosen length – nothing is re-encoded, so it's instant.
 *
 * Screen capture: Windows uses Desktop Duplication (ddagrab, GPU based); Mac uses AVFoundation;
 * Linux uses X11 (Wayland isn't supported yet). Sound (the "Sound" setting): PC sound, microphone or
 * both, from the devices AudioSources finds; without them (or if they fail) clips are video-only.
 *
 * All slow work happens on a background thread; the player hears about it through notices.
 */
public final class ClipRecorder {

    /** Something that shows the player a short message (NexusCore passes it to the game thread). */
    public interface Notices {
        void show(String message);
    }

    private static final int PIECE_SECONDS = 2;

    private final File toolsDir;
    private final File clipsDir;
    private final File bufferDir;
    private final Notices notices;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Nexus clip recorder");
        thread.setDaemon(true);
        return thread;
    });

    // Only touched on the worker thread (except the volatile ones).
    private volatile Process process;
    private String runningSetup = "";
    private int wrap;
    private List<String> encoderCache;
    private volatile boolean working; // downloading / starting – don't queue another start
    private volatile boolean gaveUp;  // FFmpeg couldn't be found/started – stop retrying until turned off and on
    private int ticks;
    private int crashes;
    private volatile boolean toldAboutSound; // the "no PC sound device" tip is shown once per session

    public ClipRecorder(File gameDir, File toolsDir, Notices notices) {
        this.toolsDir = toolsDir;
        this.clipsDir = new File(gameDir, "clips");
        this.bufferDir = new File(clipsDir, ".buffer");
        this.notices = notices;
        // When the game closes, stop FFmpeg too (it's a separate program and would keep running).
        Runtime.getRuntime().addShutdownHook(new Thread(this::killNow, "Nexus clip recorder shutdown"));
    }

    /** Called 20 times a second by NexusCore. Starts, restarts or stops recording to match the mod. */
    public void tick(Mods.ClipRecorder module, GameInfo game) {
        if (!module.isActive()) {
            gaveUp = false;
            crashes = 0;
            toldAboutSound = false;
            if (!runningSetup.isEmpty()) {
                runningSetup = "";
                worker.submit(() -> {
                    stop();
                    deleteFolder(bufferDir); // switched off – don't leave old video pieces on disk
                });
            }
            return;
        }
        if (++ticks % 40 != 0 || working || gaveUp) {
            return; // check every 2 seconds
        }
        final String setup = setupKey(module, game);
        boolean crashed = setup.equals(runningSetup) && (process == null || !process.isAlive());
        if (crashed && ++crashes > 3) {
            gaveUp = true;
            notices.show("The clip recorder keeps stopping – see clips/.buffer/ffmpeg.log. Switch it off and on to retry.");
            return;
        }
        if (!setup.equals(runningSetup) || crashed) {
            final Settings settings = new Settings(module, game);
            working = true;
            runningSetup = setup;
            worker.submit(() -> {
                try {
                    start(settings);
                } finally {
                    working = false;
                }
            });
        }
    }

    /** The player pressed the "save clip" key. */
    public void saveClip(final int seconds) {
        worker.submit(() -> {
            try {
                if (process == null || !process.isAlive()) {
                    notices.show("The clip recorder isn't running – switch on Clip Recorder in the Nexus menu.");
                    return;
                }
                notices.show("Clip saved: clips/" + save(seconds).getName());
            } catch (Exception e) {
                notices.show("Couldn't save the clip: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ starting and stopping

    /** A snapshot of what to record, taken on the game thread. */
    private static final class Settings {
        final int seconds;
        final int fps;
        final String quality;
        final int[] area; // x, y, width, height – or null for the whole screen
        final String sound; // "Off", "PC sound", "Microphone" or "PC sound + mic"

        Settings(Mods.ClipRecorder module, GameInfo game) {
            seconds = module.length.intValue();
            fps = Integer.parseInt(module.fps.get());
            quality = module.quality.get();
            int[] window = game.windowBounds();
            boolean whole = module.area.get().equals("Whole screen") || window == null || game.fullscreen();
            area = whole ? null : window;
            sound = module.sound.get();
        }
    }

    /** Recording restarts when any of these change (settings, or the window moving/resizing). */
    private static String setupKey(Mods.ClipRecorder module, GameInfo game) {
        return module.length.get() + "|" + module.fps.get() + "|" + module.quality.get() + "|"
                + module.area.get() + Arrays.toString(game.windowBounds()) + game.fullscreen() + "|" + module.sound.get();
    }

    private void start(Settings settings) {
        stop();
        toolsDir.mkdirs();
        File ffmpeg = Ffmpeg.find(toolsDir);
        if (ffmpeg == null) {
            if (!Ffmpeg.canDownload()) {
                notices.show("The clip recorder needs FFmpeg. Install it (Mac: \"brew install ffmpeg\", Linux: your"
                        + " package manager), then switch Clip Recorder off and on.");
                gaveUp = true;
                return;
            }
            try {
                notices.show("Downloading the clip recorder (about 115 MB, only once)…");
                ffmpeg = Ffmpeg.download(toolsDir, notices::show);
            } catch (IOException e) {
                notices.show("Couldn't download the clip recorder: " + e.getMessage());
                gaveUp = true;
                return;
            }
        }
        List<String> input = captureInput(settings);
        List<AudioSources.Source> sound = soundSources(ffmpeg, settings.sound);
        List<List<String>> encoders = encoderCache != null ? Arrays.asList(encoderCache) : encoderChoices(settings);
        for (List<String> encoder : encoders) {
            if (encoderCache == null && !works(ffmpeg, input, encoder)) {
                continue;
            }
            encoderCache = encoder;
            try {
                launch(ffmpeg, settings, input, encoder, sound);
                // A sound device can refuse to open (in use, unplugged…): then record without sound.
                if (!sound.isEmpty() && !stillRunningAfter(2500)) {
                    notices.show("Couldn't record sound from " + names(sound) + " – recording clips without sound.");
                    launch(ffmpeg, settings, input, encoder, new ArrayList<AudioSources.Source>());
                }
                return;
            } catch (IOException e) {
                notices.show("Couldn't start the clip recorder: " + e.getMessage());
                gaveUp = true;
                return;
            }
        }
        notices.show("The clip recorder couldn't record the screen on this PC.");
        gaveUp = true;
    }

    /** Starts the long-running FFmpeg that fills the rolling buffer. */
    private void launch(File ffmpeg, Settings settings, List<String> input, List<String> encoder,
                        List<AudioSources.Source> sound) throws IOException {
        stop();
        clearBuffer();
        int listSize = (settings.seconds + PIECE_SECONDS - 1) / PIECE_SECONDS + 1;
        wrap = listSize + 4; // spare pieces, so saving never reads a piece that's being overwritten
        List<String> command = new ArrayList<String>();
        command.add(ffmpeg.getPath());
        command.addAll(Arrays.asList("-hide_banner", "-loglevel", "error"));
        command.addAll(input); // input 0: the picture
        for (AudioSources.Source source : sound) {
            command.addAll(source.args); // inputs 1 (and 2): sound
        }
        if (sound.size() == 1) {
            command.addAll(Arrays.asList("-map", "0:v", "-map", "1:a"));
        } else if (sound.size() == 2) {
            // PC sound + microphone: mixed into one track (amix halves each, so turn it back up).
            command.addAll(Arrays.asList("-filter_complex", "[1:a][2:a]amix=inputs=2:duration=longest,volume=2[a]",
                    "-map", "0:v", "-map", "[a]"));
        }
        command.addAll(encoder);
        command.addAll(bitrate(settings));
        if (!sound.isEmpty()) {
            command.addAll(Arrays.asList("-c:a", "aac", "-b:a", "160k"));
        }
        command.addAll(Arrays.asList("-g", String.valueOf(settings.fps), // a keyframe every second
                "-f", "segment", "-segment_time", String.valueOf(PIECE_SECONDS),
                "-segment_wrap", String.valueOf(wrap),
                "-segment_list", "list.csv", "-segment_list_type", "csv",
                "-segment_list_size", String.valueOf(listSize),
                "-reset_timestamps", "1", "-segment_format", "mpegts", "piece%03d.ts"));
        ProcessBuilder builder = new ProcessBuilder(command).directory(bufferDir);
        builder.redirectErrorStream(true);
        builder.redirectOutput(new File(bufferDir, "ffmpeg.log"));
        process = builder.start();
        notices.show("Clip recorder on" + (sound.isEmpty() ? "" : " with sound (" + names(sound) + ")")
                + " – press F8 to save the last " + settings.seconds + " seconds.");
    }

    /** The sound inputs for the "Sound" setting – only the devices that exist on this PC. */
    private List<AudioSources.Source> soundSources(File ffmpeg, String choice) {
        List<AudioSources.Source> sources = new ArrayList<AudioSources.Source>();
        if (choice.startsWith("PC sound")) {
            AudioSources.Source pc = AudioSources.pcSound(ffmpeg);
            if (pc != null) {
                sources.add(pc);
            } else if (!toldAboutSound) {
                toldAboutSound = true;
                notices.show(AudioSources.tipForPcSound());
            }
        }
        if (choice.equals("Microphone") || choice.endsWith("mic")) {
            AudioSources.Source mic = AudioSources.microphone(ffmpeg);
            if (mic != null) {
                sources.add(mic);
            } else if (choice.equals("Microphone")) {
                notices.show("No microphone found – clips are recorded without sound.");
            }
        }
        return sources;
    }

    /** Waits a moment and says whether FFmpeg is still recording (false = it stopped, e.g. a bad device). */
    private boolean stillRunningAfter(long millis) {
        Process running = process;
        try {
            return running != null && !running.waitFor(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static String names(List<AudioSources.Source> sources) {
        StringBuilder text = new StringBuilder();
        for (AudioSources.Source source : sources) {
            text.append(text.length() > 0 ? " + " : "").append(source.name);
        }
        return text.toString();
    }

    /** Stops FFmpeg politely ("q" = quit), or forcefully if it doesn't listen. */
    private void stop() {
        Process running = process;
        process = null;
        if (running == null || !running.isAlive()) {
            return;
        }
        try {
            OutputStream in = running.getOutputStream();
            in.write('q');
            in.flush();
            if (!running.waitFor(3, TimeUnit.SECONDS)) {
                running.destroyForcibly();
            }
        } catch (Exception e) {
            running.destroyForcibly();
        }
    }

    private void killNow() {
        Process running = process;
        if (running != null) {
            running.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------ FFmpeg settings

    /** Where the picture comes from, per operating system. */
    private static List<String> captureInput(Settings s) {
        List<String> args = new ArrayList<String>();
        if (Ffmpeg.isWindows()) {
            StringBuilder grab = new StringBuilder("ddagrab=output_idx=0:draw_mouse=0:framerate=" + s.fps);
            if (s.area != null) {
                grab.append(":offset_x=").append(Math.max(0, s.area[0])).append(":offset_y=").append(Math.max(0, s.area[1]))
                        .append(":video_size=").append(even(s.area[2])).append('x').append(even(s.area[3]));
            }
            args.addAll(Arrays.asList("-f", "lavfi", "-i", grab.toString()));
        } else if (Ffmpeg.isMac()) {
            args.addAll(Arrays.asList("-f", "avfoundation", "-capture_cursor", "0", "-framerate", String.valueOf(s.fps),
                    "-i", "Capture screen 0:none"));
        } else {
            String display = System.getenv("DISPLAY") == null ? ":0" : System.getenv("DISPLAY");
            if (s.area != null) {
                args.addAll(Arrays.asList("-f", "x11grab", "-framerate", String.valueOf(s.fps),
                        "-video_size", even(s.area[2]) + "x" + even(s.area[3]),
                        "-i", display + "+" + Math.max(0, s.area[0]) + "," + Math.max(0, s.area[1])));
            } else {
                args.addAll(Arrays.asList("-f", "x11grab", "-framerate", String.valueOf(s.fps), "-i", display));
            }
        }
        return args;
    }

    /** Video encoders to try, best first (graphics-card encoders, then the CPU). */
    private static List<List<String>> encoderChoices(Settings s) {
        List<List<String>> choices = new ArrayList<List<String>>();
        if (Ffmpeg.isWindows()) {
            // NVIDIA can read the captured picture straight from the graphics card.
            choices.add(Arrays.asList("-c:v", "h264_nvenc", "-preset", "p1", "-tune", "ll", "-rc", "vbr"));
            // AMD and Intel get a copy first (tested on AMD: the direct route doesn't work).
            choices.add(Arrays.asList("-vf", "hwdownload,format=bgra,format=nv12", "-c:v", "h264_amf",
                    "-quality", "speed", "-rc", "vbr_peak"));
            choices.add(Arrays.asList("-vf", "hwdownload,format=bgra,format=nv12", "-c:v", "h264_qsv",
                    "-preset", "veryfast"));
            choices.add(Arrays.asList("-vf", "hwdownload,format=bgra,format=yuv420p", "-c:v", "libx264",
                    "-preset", "ultrafast", "-tune", "zerolatency"));
        } else if (Ffmpeg.isMac()) {
            choices.add(Arrays.asList("-pix_fmt", "nv12", "-c:v", "h264_videotoolbox", "-realtime", "1"));
            choices.add(Arrays.asList("-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast"));
        } else {
            choices.add(Arrays.asList("-pix_fmt", "yuv420p", "-c:v", "h264_nvenc", "-preset", "p1"));
            choices.add(Arrays.asList("-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast"));
        }
        return choices;
    }

    /** Video quality → bitrate, scaled to the picture size and frame rate (Medium = 12 Mbit/s at 1080p60). */
    private static List<String> bitrate(Settings s) {
        double base = s.quality.equals("High") ? 20 : s.quality.equals("Low") ? 6 : 12;
        double pixels = s.area == null ? 1920 * 1080 : (double) s.area[2] * s.area[3];
        int mbit = (int) Math.max(2, Math.round(base * pixels / (1920 * 1080) * s.fps / 60.0));
        return Arrays.asList("-b:v", mbit + "M", "-maxrate", (mbit * 4 / 3) + "M", "-bufsize", (mbit * 2) + "M");
    }

    /** Test-records 20 frames (thrown away) to see if this encoder works on this PC. */
    private boolean works(File ffmpeg, List<String> input, List<String> encoder) {
        List<String> command = new ArrayList<String>();
        command.add(ffmpeg.getPath());
        command.addAll(Arrays.asList("-hide_banner", "-loglevel", "error"));
        command.addAll(input);
        command.addAll(Arrays.asList("-frames:v", "20"));
        command.addAll(encoder);
        command.addAll(Arrays.asList("-f", "null", "-"));
        try {
            Process test = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(new File(toolsDir, "ffmpeg-test.log")).start();
            if (!test.waitFor(15, TimeUnit.SECONDS)) {
                test.destroyForcibly();
                return false;
            }
            return test.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ saving

    /** Joins the newest pieces and trims them to the last `seconds`. */
    private File save(int seconds) throws IOException, InterruptedException {
        List<String> pieces = newestPieces(seconds);
        if (pieces.isEmpty()) {
            throw new IOException("nothing recorded yet – wait a few seconds");
        }
        // Copy the pieces first, so FFmpeg can't overwrite one while we're reading it.
        File work = new File(clipsDir, ".saving");
        deleteFolder(work);
        if (!work.mkdirs()) {
            throw new IOException("couldn't create " + work);
        }
        StringBuilder list = new StringBuilder();
        for (String piece : pieces) {
            File from = new File(bufferDir, piece);
            if (from.isFile()) {
                copy(from, new File(work, piece));
                list.append("file '").append(piece).append("'\n");
            }
        }
        writeText(new File(work, "list.txt"), list.toString());

        File ffmpeg = Ffmpeg.find(toolsDir);
        String name = "Nexus-clip-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".mp4";
        File clip = new File(clipsDir, name);
        run(work, ffmpeg.getPath(), "-hide_banner", "-loglevel", "error", "-f", "concat", "-safe", "0",
                "-i", "list.txt", "-c", "copy", "joined.mp4");
        run(work, ffmpeg.getPath(), "-hide_banner", "-loglevel", "error", "-sseof", "-" + seconds,
                "-i", "joined.mp4", "-c", "copy", "-movflags", "+faststart", clip.getAbsolutePath());
        deleteFolder(work);
        return clip;
    }

    /** The finished pieces covering the last `seconds`, plus the piece being recorded right now. */
    private List<String> newestPieces(int seconds) throws IOException {
        List<String> finished = new ArrayList<String>();
        File list = new File(bufferDir, "list.csv");
        if (list.isFile()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(list), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        finished.add(line.split(",")[0]);
                    }
                }
            } finally {
                reader.close();
            }
        }
        int needed = (seconds + PIECE_SECONDS - 1) / PIECE_SECONDS + 1;
        List<String> pieces = new ArrayList<String>(finished.subList(Math.max(0, finished.size() - needed), finished.size()));
        // The piece after the newest finished one is still being written – include it too.
        int nextIndex = finished.isEmpty() ? 0 : (pieceNumber(finished.get(finished.size() - 1)) + 1) % wrap;
        String current = String.format("piece%03d.ts", nextIndex);
        if (new File(bufferDir, current).isFile()) {
            pieces.add(current);
        }
        return pieces;
    }

    private static int pieceNumber(String name) {
        return Integer.parseInt(name.replaceAll("\\D", ""));
    }

    private static void run(File folder, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(folder).redirectErrorStream(true)
                .redirectOutput(new File(folder, "save.log")).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("saving took too long");
        }
        if (process.exitValue() != 0) {
            throw new IOException("FFmpeg couldn't join the video");
        }
    }

    // ------------------------------------------------------------------ small file helpers

    private void clearBuffer() throws IOException {
        deleteFolder(bufferDir);
        if (!bufferDir.mkdirs()) {
            throw new IOException("couldn't create " + bufferDir);
        }
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        folder.delete();
    }

    private static void copy(File from, File to) throws IOException {
        java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeText(File file, String text) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static int even(int value) {
        return value - value % 2; // video sizes must be even numbers
    }
}
