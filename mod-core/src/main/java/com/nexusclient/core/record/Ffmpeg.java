package com.nexusclient.core.record;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Ffmpeg – finds FFmpeg, the free video tool the clip recorder uses (it runs as its own program,
 * next to the game). FFmpeg is never bundled inside Nexus:
 *  - Windows: downloaded once from gyan.dev (the "essentials" build, ~115 MB, checked against its
 *    published SHA-256 fingerprint), into the Nexus tools folder.
 *  - Mac/Linux: uses FFmpeg if it's installed (e.g. "brew install ffmpeg" / the system package).
 */
public final class Ffmpeg {

    private static final String WINDOWS_ZIP = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
    private static final String WINDOWS_SHA256 = WINDOWS_ZIP + ".sha256";

    /** Something that wants to hear how a download is going ("Downloading… 40%"). */
    public interface Progress {
        void update(String message);
    }

    private Ffmpeg() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /** Where Nexus keeps its own copy (Windows). */
    private static File ownCopy(File toolsDir) {
        return new File(new File(toolsDir, "ffmpeg"), isWindows() ? "ffmpeg.exe" : "ffmpeg");
    }

    /** The FFmpeg program to use, or null if there isn't one yet. */
    public static File find(File toolsDir) {
        File own = ownCopy(toolsDir);
        if (own.isFile()) {
            return own;
        }
        String name = isWindows() ? "ffmpeg.exe" : "ffmpeg";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File candidate = new File(dir, name);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        // Homebrew and common Linux places (the game may not see the shell's PATH).
        for (String dir : new String[] {"/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"}) {
            File candidate = new File(dir, name);
            if (!isWindows() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /** True where Nexus can download FFmpeg by itself (Windows). */
    public static boolean canDownload() {
        return isWindows();
    }

    /** Downloads FFmpeg (Windows), checks its fingerprint, and keeps just ffmpeg.exe. */
    public static File download(File toolsDir, Progress progress) throws IOException {
        File target = ownCopy(toolsDir);
        File folder = target.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Couldn't create " + folder);
        }
        String expected = readText(WINDOWS_SHA256).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        File zip = new File(folder, "ffmpeg-download.zip");
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            HttpURLConnection connection = open(WINDOWS_ZIP);
            long total = connection.getContentLengthLong();
            InputStream in = connection.getInputStream();
            OutputStream out = new FileOutputStream(zip);
            try {
                byte[] buffer = new byte[1 << 16];
                long done = 0;
                int lastPercent = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                    done += read;
                    int percent = total > 0 ? (int) (done * 100 / total) : 0;
                    if (percent >= lastPercent + 25 && percent < 100) {
                        lastPercent = percent - percent % 25;
                        progress.update("Downloading the clip recorder… " + lastPercent + "%");
                    }
                }
            } finally {
                out.close();
                in.close();
            }
            if (!hex(sha256.digest()).equals(expected)) {
                throw new IOException("the download was damaged (its fingerprint didn't match) – try again");
            }
            extractFfmpeg(zip, target);
            return target;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            zip.delete();
        }
    }

    /** Copies bin/ffmpeg.exe out of the zip (the rest isn't needed). */
    private static void extractFfmpeg(File zip, File target) throws IOException {
        ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(zip));
        try {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().endsWith("/bin/ffmpeg.exe")) {
                    File temp = new File(target.getPath() + ".part");
                    OutputStream out = new FileOutputStream(temp);
                    try {
                        byte[] buffer = new byte[1 << 16];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                    } finally {
                        out.close();
                    }
                    if (!temp.renameTo(target)) {
                        throw new IOException("Couldn't save " + target);
                    }
                    return;
                }
            }
        } finally {
            in.close();
        }
        throw new IOException("ffmpeg.exe wasn't in the download");
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", "NexusClient");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private static String readText(String url) throws IOException {
        InputStream in = open(url).getInputStream();
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) {
            text.append(String.format("%02x", b & 0xFF));
        }
        return text.toString();
    }
}
