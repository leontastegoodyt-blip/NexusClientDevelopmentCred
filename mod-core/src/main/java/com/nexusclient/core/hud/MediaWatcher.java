package com.nexusclient.core.hud;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * MediaWatcher – finds out which song is playing on the computer, for the Now Playing HUD mod.
 *
 *   Windows – the system media controls (the same ones the volume pop-up shows), so it works with
 *             Spotify, YouTube in a browser, Apple Music, the Media Player… A small PowerShell script
 *             runs in the background and prints the song every 2 seconds; it closes itself when the
 *             game closes.
 *   Mac     – asks Spotify or Apple Music (osascript) every 3 seconds, only if they're open.
 *   Linux   – asks "playerctl" (works with most players) every 3 seconds, if it's installed.
 *
 * Nothing is sent anywhere – it's only shown on the player's own screen.
 */
public final class MediaWatcher {

    /** The song right now (one object, replaced as a whole, so it's always consistent). */
    public static final class Song {
        public final String title;
        public final String artist;
        public final boolean playing;
        public final int position;   // seconds, when read
        public final int length;     // seconds (0 = unknown)
        public final long readAt = System.currentTimeMillis();

        Song(String title, String artist, boolean playing, int position, int length) {
            this.title = title;
            this.artist = artist;
            this.playing = playing;
            this.position = position;
            this.length = length;
        }

        /** Where the song is now (moves on between reads while it plays). */
        public int positionNow() {
            int moved = playing ? (int) ((System.currentTimeMillis() - readAt) / 1000) : 0;
            return length > 0 ? Math.min(length, position + moved) : position + moved;
        }
    }

    private static volatile Song song;
    private static volatile boolean running;
    private static volatile Process process;

    private MediaWatcher() {}

    /** The song playing (or paused) right now, or null if none / not known. */
    public static Song current() {
        Song now = song;
        return now == null || now.title.isEmpty() ? null : now;
    }

    /** Starts watching (once). */
    public static synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                try {
                    if (os.contains("win")) {
                        watchWindows();
                    } else {
                        watchByAsking(os.contains("mac"));
                    }
                } catch (Exception e) {
                    // No media info on this PC – Now Playing just stays empty.
                } finally {
                    running = false;
                }
            }
        }, "Nexus now playing");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the background helper (Now Playing switched off). */
    public static synchronized void stop() {
        running = false;
        Process p = process;
        if (p != null) {
            p.destroy();
        }
        song = null;
    }

    // ------------------------------------------------------------------ Windows

    private static void watchWindows() throws Exception {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String script = ""
                + "Add-Type -AssemblyName System.Runtime.WindowsRuntime\n"
                + "$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and "
                + "$_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n"
                + "function Await($op, [Type]$type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); "
                + "$t.Wait(-1) | Out-Null; $t.Result }\n"
                + "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, "
                + "ContentType = WindowsRuntime] | Out-Null\n"
                + "$m = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) "
                + "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n"
                + "while ($true) {\n"
                + "  if (-not (Get-Process -Id " + pid + " -ErrorAction SilentlyContinue)) { exit }\n" // the game closed
                + "  try {\n"
                + "    $s = $m.GetCurrentSession()\n"
                + "    if ($s) {\n"
                + "      $p = Await ($s.TryGetMediaPropertiesAsync()) "
                + "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n"
                + "      $i = $s.GetPlaybackInfo(); $t = $s.GetTimelineProperties()\n"
                + "      [Console]::Out.WriteLine(\"NP`t$($p.Title)`t$($p.Artist)`t$($i.PlaybackStatus)`t"
                + "$([int]$t.Position.TotalSeconds)`t$([int]$t.EndTime.TotalSeconds)\")\n"
                + "    } else { [Console]::Out.WriteLine(\"NP`t`t`tNone`t0`t0\") }\n"
                + "  } catch { [Console]::Out.WriteLine(\"NP`t`t`tNone`t0`t0\") }\n"
                + "  [Console]::Out.Flush(); Start-Sleep -Seconds 2\n"
                + "}\n";
        // -EncodedCommand takes the script as Base64 of UTF-16 text (so no quoting problems).
        String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes("UTF-16LE"));
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded).redirectErrorStream(true).start();
        process = p;
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        while (running && (line = reader.readLine()) != null) {
            read(line);
        }
        p.destroy();
    }

    // ------------------------------------------------------------------ Mac and Linux

    private static final String MAC_SCRIPT = ""
            + "if application \"Spotify\" is running then\n"
            + "  tell application \"Spotify\"\n"
            + "    if player state is not stopped then return \"NP\" & tab & (name of current track) & tab & (artist of current track) & tab & (player state as string) & tab & (round (player position)) & tab & (round ((duration of current track) / 1000))\n"
            + "  end tell\n"
            + "end if\n"
            + "if application \"Music\" is running then\n"
            + "  tell application \"Music\"\n"
            + "    if player state is not stopped then return \"NP\" & tab & (name of current track) & tab & (artist of current track) & tab & (player state as string) & tab & (round (player position)) & tab & (round (duration of current track))\n"
            + "  end tell\n"
            + "end if\n"
            + "return \"NP\" & tab & \"\" & tab & \"\" & tab & \"None\" & tab & 0 & tab & 0";

    private static void watchByAsking(boolean mac) throws Exception {
        while (running) {
            ProcessBuilder builder = mac ? new ProcessBuilder("osascript", "-e", MAC_SCRIPT)
                    : new ProcessBuilder("playerctl", "metadata", "--format",
                    "NP\t{{title}}\t{{artist}}\t{{status}}\t{{position}}\t{{mpris:length}}");
            try {
                Process p = builder.redirectErrorStream(true).start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                String line = reader.readLine();
                p.waitFor();
                if (line != null && !mac) {
                    // playerctl gives microseconds – turn them into seconds.
                    String[] parts = line.split("\t", -1);
                    if (parts.length >= 6) {
                        parts[4] = String.valueOf(parseLong(parts[4]) / 1000000);
                        parts[5] = String.valueOf(parseLong(parts[5]) / 1000000);
                        line = join(parts);
                    }
                }
                read(line == null || !line.startsWith("NP") ? "NP\t\t\tNone\t0\t0" : line);
            } catch (Exception e) {
                read("NP\t\t\tNone\t0\t0"); // e.g. playerctl isn't installed
                Thread.sleep(27000);         // …so ask much less often
            }
            Thread.sleep(3000);
        }
    }

    /** One line: NP, title, artist, status, position (s), length (s). */
    static void read(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 6 || !parts[0].equals("NP")) {
            return;
        }
        String status = parts[3].toLowerCase(Locale.ROOT);
        song = new Song(parts[1].trim(), parts[2].trim(), status.startsWith("play"), (int) parseLong(parts[4]),
                (int) parseLong(parts[5]));
    }

    private static long parseLong(String text) {
        try {
            return (long) Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String join(String[] parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            out.append(i > 0 ? "\t" : "").append(parts[i]);
        }
        return out.toString();
    }
}
