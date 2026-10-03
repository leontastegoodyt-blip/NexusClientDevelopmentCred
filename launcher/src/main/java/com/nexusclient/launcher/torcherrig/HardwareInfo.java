package com.nexusclient.launcher.torcherrig;

import java.util.List;

/**
 * HardwareInfo – what TorcherRig found out about the PC.
 *
 * @param cpuName      e.g. "AMD Ryzen 7 5800X 8-Core Processor"
 * @param cpuCores     physical cores (the real ones)
 * @param cpuThreads   logical cores ("threads" – can be double the cores)
 * @param cpuGhz       top speed in GHz (0 if unknown)
 * @param gpus         the graphics cards (a laptop can have two)
 * @param totalRamMb   all the memory (RAM) in the PC, in MB
 * @param freeRamMb    memory not being used right now, in MB
 * @param screenWidth  main monitor width in pixels
 * @param screenHeight main monitor height in pixels
 * @param refreshRate  main monitor refresh rate in Hz (how many pictures per second it can show)
 * @param osName       e.g. "Microsoft Windows 11"
 */
public record HardwareInfo(String cpuName, int cpuCores, int cpuThreads, double cpuGhz,
                           List<Gpu> gpus, long totalRamMb, long freeRamMb,
                           int screenWidth, int screenHeight, int refreshRate, String osName) {

    /** One graphics card. vramMb = its own video memory, in MB (0 if unknown). */
    public record Gpu(String name, long vramMb) {}

    /**
     * A short description of the main hardware. If this changes later, the player has new hardware
     * and TorcherRig should be run again.
     */
    public String fingerprint() {
        StringBuilder text = new StringBuilder(cpuName);
        for (Gpu gpu : gpus) {
            text.append(" | ").append(gpu.name());
        }
        text.append(" | ").append(Math.round(totalRamMb / 1024.0)).append(" GB");
        return text.toString();
    }
}
