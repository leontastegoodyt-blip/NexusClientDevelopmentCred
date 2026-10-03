package com.nexusclient.core.hud;

/**
 * FrameTimes – remembers how long each of the last few hundred frames took, for the frame-time
 * graph. Smooth gameplay = every frame takes about the same time; spikes = stutters.
 * NexusCore calls frame() once per drawn frame.
 */
public final class FrameTimes {

    /** How many frames are remembered (about 2–4 seconds of play). */
    public static final int SIZE = 240;

    private static final float[] millis = new float[SIZE];
    private static int next;
    private static int count;
    private static long lastNanos;

    private FrameTimes() {}

    /** A frame was just drawn. */
    public static synchronized void frame() {
        long now = System.nanoTime();
        if (lastNanos != 0) {
            float ms = (now - lastNanos) / 1_000_000f;
            if (ms < 1000) { // ignore huge gaps (e.g. the game was paused or loading)
                millis[next] = ms;
                next = (next + 1) % SIZE;
                count = Math.min(count + 1, SIZE);
            }
        }
        lastNanos = now;
    }

    /** The remembered frame times in milliseconds, oldest first. */
    public static synchronized float[] snapshot() {
        float[] copy = new float[count];
        int start = (next - count + SIZE) % SIZE;
        for (int i = 0; i < count; i++) {
            copy[i] = millis[(start + i) % SIZE];
        }
        return copy;
    }

    /** Average frame time (ms) of the given frames. */
    public static float average(float[] frames) {
        if (frames.length == 0) {
            return 0;
        }
        float total = 0;
        for (float f : frames) {
            total += f;
        }
        return total / frames.length;
    }

    /**
     * "1% low" FPS: the frame rate of the slowest 1% of frames. A big gap between this and the
     * normal FPS means stutter, even when the average FPS looks high.
     */
    public static int onePercentLowFps(float[] frames) {
        if (frames.length == 0) {
            return 0;
        }
        float[] sorted = frames.clone();
        java.util.Arrays.sort(sorted);
        int slowest = Math.max(1, sorted.length / 100);
        float total = 0;
        for (int i = sorted.length - slowest; i < sorted.length; i++) {
            total += sorted[i];
        }
        float ms = total / slowest;
        return ms <= 0 ? 0 : Math.round(1000f / ms);
    }
}
