package com.nexusclient.launcher.game;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloader – downloads files from the internet into the Nexus folder.
 *
 * It's careful in a few ways:
 *   - Skips files that are already downloaded, so the second launch is fast.
 *   - Checks every download against its "SHA-1" fingerprint (a code Mojang publishes for each
 *     file). If even one byte is wrong, the fingerprint won't match and we download it again.
 *   - Downloads into a ".part" file first, and only renames it when it's complete, so a
 *     half-finished download never looks like a real file.
 *   - Downloads several files at the same time, which is much faster for thousands of small files.
 *
 * "AutoCloseable" means it can be used in a try-with-resources block, which closes the internet
 * connection automatically when we're done.
 */
public final class Downloader implements AutoCloseable {

    /**
     * One file to download.
     *
     * @param url    where to download it from
     * @param target where to save it
     * @param sha1   its fingerprint, to check it downloaded correctly (null if unknown)
     * @param size   its size in bytes (0 if unknown)
     */
    public record Job(String url, Path target, String sha1, long size) {}

    private static final int PARALLEL_DOWNLOADS = 8; // how many files to download at once
    private static final int ATTEMPTS = 3;           // how many times to try each file

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** Downloads a small text file (like a JSON list) and returns its contents. */
    public String fetchString(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request(url), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Server answered with error " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    /** Downloads one file, unless it's already there and correct. Tries up to 3 times. */
    public void download(Job job) throws IOException, InterruptedException {
        if (isUpToDate(job)) {
            return;
        }
        Files.createDirectories(job.target().getParent());
        Path part = job.target().resolveSibling(job.target().getFileName() + ".part");

        IOException lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<Path> response = client.send(request(job.url()), HttpResponse.BodyHandlers.ofFile(part));
                if (response.statusCode() != 200) {
                    throw new IOException("Server answered with error " + response.statusCode() + " for " + job.url());
                }
                if (job.sha1() != null && !job.sha1().equalsIgnoreCase(sha1(part))) {
                    throw new IOException("The downloaded file was damaged: " + job.url());
                }
                Files.move(part, job.target(), StandardCopyOption.REPLACE_EXISTING);
                return; // success
            } catch (IOException e) {
                lastError = e; // try again
            }
        }
        Files.deleteIfExists(part);
        throw lastError;
    }

    /**
     * Downloads many files, several at a time, reporting progress as it goes.
     *
     * @param label shown in the progress message, e.g. "Downloading Minecraft 1.8.9"
     */
    public void downloadAll(List<Job> jobs, String label, ProgressListener progress)
            throws IOException, InterruptedException {

        // The same file can be listed twice (e.g. two sounds that are identical). Keep one of each.
        Map<Path, Job> unique = new LinkedHashMap<>();
        for (Job job : jobs) {
            unique.putIfAbsent(job.target(), job);
        }
        // Only download what's missing or wrong.
        List<Job> needed = new ArrayList<>();
        for (Job job : unique.values()) {
            if (!isUpToDate(job)) {
                needed.add(job);
            }
        }
        if (needed.isEmpty()) {
            return;
        }

        long totalBytes = needed.stream().mapToLong(job -> Math.max(job.size(), 1)).sum();
        // "Atomic" numbers can safely be added to by several download threads at the same time.
        AtomicLong doneBytes = new AtomicLong();
        AtomicInteger doneFiles = new AtomicInteger();
        progress.update(label + "…", 0);

        // A "thread pool": a small team of worker threads that each take the next download job.
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_DOWNLOADS);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (Job job : needed) {
                results.add(pool.submit(() -> {
                    download(job);
                    long bytes = doneBytes.addAndGet(Math.max(job.size(), 1));
                    int files = doneFiles.incrementAndGet();
                    progress.update(label + "  ·  " + files + " / " + needed.size() + " files  ·  "
                            + megabytes(bytes) + " / " + megabytes(totalBytes), (double) bytes / totalBytes);
                    return null;
                }));
            }
            // Wait for every job. If one failed, pass its error on.
            for (Future<?> result : results) {
                try {
                    result.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException io) {
                        throw io;
                    }
                    throw new IOException(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow(); // stop the workers (also cancels the rest if something failed)
        }
    }

    /** True if the file already exists and looks right, so it doesn't need downloading. */
    public static boolean isUpToDate(Job job) throws IOException {
        if (!Files.isRegularFile(job.target())) {
            return false;
        }
        if (job.size() > 0) {
            return Files.size(job.target()) == job.size(); // quick check for big file lists
        }
        if (job.sha1() != null) {
            return job.sha1().equalsIgnoreCase(sha1(job.target()));
        }
        return true;
    }

    /** Calculates a file's SHA-1 fingerprint, as 40 hex characters. */
    public static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is always available in Java", e);
        }
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "NexusClient/0.1.0")
                .GET()
                .build();
    }

    private static String megabytes(long bytes) {
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

    @Override
    public void close() {
        client.close();
    }
}
