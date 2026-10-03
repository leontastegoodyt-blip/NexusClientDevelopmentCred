package com.nexusclient.launcher.torcherrig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleConsumer;

/**
 * Benchmark – TorcherRig's optional quick speed test (about 10 seconds).
 *
 * Reading the hardware's name only tells part of the story (a laptop CPU with a fancy name can be
 * slow when it's hot or on battery). So this measures real speed: it does work similar to what
 * Minecraft does when it builds the world – lots of maths on grids of numbers – and counts how much
 * gets done.
 *
 *   - 5 seconds on ONE core   (Minecraft's main game loop mostly runs on one core)
 *   - 5 seconds on ALL cores  (world loading and chunk building use many cores)
 *
 * The result is a score where about 100 = a typical mid-range gaming PC.
 */
public final class Benchmark {

    /** @param singleCore score for one core, multiCore score for all cores (100 ≈ mid-range PC) */
    public record Result(int singleCore, int multiCore) {}

    private static final long PHASE_MILLIS = 5_000;

    // How many work rounds a typical mid-range gaming PC (Ryzen 5 5600-class desktop) does in one
    // phase. These were ESTIMATED from a measured Ryzen 5 7520U laptop (167,871 single / 733,950 multi)
    // and typical speed differences between the two chips. They only turn raw counts into friendly
    // scores around 100 – re-measure on a real mid-range PC when one is available.
    private static final double REFERENCE_SINGLE = 243_000;
    private static final double REFERENCE_MULTI = 1_900_000;

    private Benchmark() {}

    /**
     * Runs the test. Takes about 10 seconds – run it on a background thread.
     *
     * @param progress told how far along the test is, from 0.0 to 1.0
     */
    public static Result run(DoubleConsumer progress) throws InterruptedException {
        // Phase 1: one core.
        long singleRounds = workFor(PHASE_MILLIS, 0, progress);

        // Phase 2: every core at once.
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long multiRounds = 0;
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                boolean reportsProgress = i == 0; // only one worker updates the progress bar
                results.add(pool.submit(() -> workFor(PHASE_MILLIS, 0.5, reportsProgress ? progress : p -> {})));
            }
            for (Future<Long> result : results) {
                multiRounds += result.get();
            }
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Speed test failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }
        progress.accept(1.0);

        return new Result((int) Math.round(singleRounds / REFERENCE_SINGLE * 100),
                (int) Math.round(multiRounds / REFERENCE_MULTI * 100));
    }

    /** Does rounds of work until the time is up, and returns how many rounds were finished. */
    private static long workFor(long millis, double progressStart, DoubleConsumer progress) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        long start = System.currentTimeMillis();
        long rounds = 0;
        int[] grid = new int[16 * 16 * 64]; // a small "chunk" of blocks
        long seed = 12345;
        while (System.currentTimeMillis() < end) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            // Fill the chunk with pseudo-random "terrain" and then smooth it – maths-heavy like world generation.
            for (int i = 0; i < grid.length; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                grid[i] = (int) (seed >>> 40);
            }
            long sum = 0;
            for (int i = 1; i < grid.length - 1; i++) {
                sum += (grid[i - 1] + grid[i] * 2 + grid[i + 1]) >> 2;
            }
            if (sum == 42) {
                seed++; // keeps Java from skipping the work as "unused"
            }
            rounds++;
            if ((rounds & 63) == 0) {
                progress.accept(progressStart + 0.5 * (System.currentTimeMillis() - start) / millis);
            }
        }
        return rounds;
    }
}
