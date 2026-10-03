package com.nexusclient.launcher.game;

/**
 * ProgressListener – something that wants to hear how a long job (like downloading) is going.
 * The Launch bar uses one to update its message and progress bar.
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param message  what's happening, in plain words, e.g. "Downloading Java 8 (120 / 410 files)"
     * @param fraction how far along it is, from 0.0 (just started) to 1.0 (done),
     *                 or -1 if we can't tell (the bar then shows a moving "busy" animation)
     */
    void update(String message, double fraction);
}
