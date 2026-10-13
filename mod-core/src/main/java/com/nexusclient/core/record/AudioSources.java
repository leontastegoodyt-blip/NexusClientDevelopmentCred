package com.nexusclient.core.record;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AudioSources – finds where the clip recorder can get sound from, per operating system.
 *
 * "PC sound" means everything the computer plays (the game, music, voice chat). Windows, Mac and Linux
 * hand that to recording programs differently:
 *   - Windows: a recording device that carries the PC's sound, like "Stereo Mix" (built into many
 *     Realtek sound chips, but often switched off) or a virtual cable (VB-CABLE, VoiceMeeter).
 *     FFmpeg reads it through DirectShow ("dshow").
 *   - Mac: macOS can't record its own sound without a helper such as BlackHole or Loopback.
 *   - Linux: PulseAudio / PipeWire offer a "monitor" of the speakers.
 * "Microphone" is the first other recording device.
 *
 * Each source is a list of FFmpeg arguments for one extra input. Nothing is found → null, and
 * tipForPcSound() says how to switch it on. Developer test: -Dnexus.clipSoundTest=true uses two test
 * tones instead (the developer sandbox has no sound devices).
 */
final class AudioSources {

    /** One sound input for FFmpeg. */
    static final class Source {
        final String name;       // for messages, e.g. "Stereo Mix (Realtek Audio)"
        final List<String> args; // e.g. -f dshow -i audio=…

        Source(String name, List<String> args) {
            this.name = name;
            this.args = args;
        }
    }

    /** Words in the names of devices that carry the PC's own sound. */
    private static final String[] PC_SOUND_WORDS = {"stereo mix", "what u hear", "wave out", "loopback",
            "cable output", "voicemeeter", "virtual-audio-capturer", "blackhole", "soundflower", "monitor"};

    private AudioSources() {}

    static Source pcSound(File ffmpeg) {
        if (Boolean.getBoolean("nexus.clipSoundTest")) {
            return new Source("test tone", lavfi("sine=frequency=440:sample_rate=48000"));
        }
        if (Ffmpeg.isWindows()) {
            for (String device : windowsDevices(ffmpeg)) {
                if (isPcSound(device)) {
                    return new Source(device, dshow(device));
                }
            }
            return null;
        }
        if (Ffmpeg.isMac()) {
            List<String> devices = macDevices(ffmpeg);
            for (int i = 0; i < devices.size(); i++) {
                if (isPcSound(devices.get(i))) {
                    return new Source(devices.get(i), avfoundation(i));
                }
            }
            return null;
        }
        String sink = firstLine("pactl", "get-default-sink");
        if (sink == null || sink.isEmpty()) {
            return null;
        }
        return new Source("speakers", Arrays.asList("-thread_queue_size", "1024", "-f", "pulse", "-i", sink + ".monitor"));
    }

    static Source microphone(File ffmpeg) {
        if (Boolean.getBoolean("nexus.clipSoundTest")) {
            return new Source("test tone 2", lavfi("sine=frequency=660:sample_rate=44100"));
        }
        if (Ffmpeg.isWindows()) {
            for (String device : windowsDevices(ffmpeg)) {
                if (!isPcSound(device)) {
                    return new Source(device, dshow(device));
                }
            }
            return null;
        }
        if (Ffmpeg.isMac()) {
            List<String> devices = macDevices(ffmpeg);
            for (int i = 0; i < devices.size(); i++) {
                if (!isPcSound(devices.get(i))) {
                    return new Source(devices.get(i), avfoundation(i));
                }
            }
            return null;
        }
        return new Source("microphone", Arrays.asList("-thread_queue_size", "1024", "-f", "pulse", "-i", "default"));
    }

    /** How to make "PC sound" available on this system (shown when none was found). */
    static String tipForPcSound() {
        if (Ffmpeg.isWindows()) {
            return "Clips have no PC sound: turn on \"Stereo Mix\" (Windows Sound settings → More sound settings"
                    + " → Recording → right-click → Show disabled devices → Enable), or install VB-CABLE.";
        }
        if (Ffmpeg.isMac()) {
            return "Clips have no PC sound: macOS needs a helper like BlackHole (free) to record its own sound.";
        }
        return "Clips have no PC sound: PulseAudio/PipeWire (pactl) wasn't found.";
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isPcSound(String device) {
        String lower = device.toLowerCase(Locale.ROOT);
        for (String word : PC_SOUND_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> lavfi(String source) {
        return Arrays.asList("-thread_queue_size", "1024", "-f", "lavfi", "-i", source);
    }

    private static List<String> dshow(String device) {
        // A small buffer (50 ms) keeps the sound in step with the picture.
        return Arrays.asList("-thread_queue_size", "1024", "-f", "dshow", "-audio_buffer_size", "50",
                "-i", "audio=" + device);
    }

    private static List<String> avfoundation(int index) {
        return Arrays.asList("-thread_queue_size", "1024", "-f", "avfoundation", "-i", ":" + index);
    }

    /** Windows recording devices, as FFmpeg lists them: lines like  "Stereo Mix (Realtek Audio)" (audio). */
    private static List<String> windowsDevices(File ffmpeg) {
        List<String> devices = new ArrayList<String>();
        Pattern audio = Pattern.compile("\"(.+)\" \\(audio\\)");
        for (String line : output(ffmpeg.getPath(), "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy")) {
            Matcher m = audio.matcher(line);
            if (m.find()) {
                devices.add(m.group(1));
            }
        }
        return devices;
    }

    /** Mac audio devices in FFmpeg's order (the index is what FFmpeg wants). */
    private static List<String> macDevices(File ffmpeg) {
        List<String> devices = new ArrayList<String>();
        boolean inAudio = false;
        Pattern entry = Pattern.compile("\\[(\\d+)\\] (.+)$");
        for (String line : output(ffmpeg.getPath(), "-hide_banner", "-f", "avfoundation", "-list_devices", "true", "-i", "")) {
            if (line.contains("audio devices:")) {
                inAudio = true;
            } else if (inAudio) {
                Matcher m = entry.matcher(line);
                if (m.find()) {
                    devices.add(m.group(2).trim());
                }
            }
        }
        return devices;
    }

    private static String firstLine(String... command) {
        List<String> lines = output(command);
        return lines.isEmpty() ? null : lines.get(0).trim();
    }

    /** Runs a short command and returns everything it printed (empty if it couldn't run). */
    private static List<String> output(String... command) {
        List<String> lines = new ArrayList<String>();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } finally {
                reader.close();
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            // not available on this PC
        }
        return lines;
    }
}
