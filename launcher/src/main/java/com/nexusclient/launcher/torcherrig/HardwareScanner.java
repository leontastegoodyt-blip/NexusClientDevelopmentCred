package com.nexusclient.launcher.torcherrig;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

/**
 * HardwareScanner – reads the PC's hardware using the OSHI library (the project brief, TorcherRig).
 * Takes a second or two, so run it on a background thread.
 */
public final class HardwareScanner {

    private HardwareScanner() {}

    public static HardwareInfo scan() {
        SystemInfo system = new SystemInfo();
        HardwareAbstractionLayer hardware = system.getHardware();

        // --- Processor (CPU) ---
        CentralProcessor cpu = hardware.getProcessor();
        String cpuName = cpu.getProcessorIdentifier().getName().trim();
        long maxHz = cpu.getMaxFreq();
        if (maxHz <= 0) {
            maxHz = cpu.getProcessorIdentifier().getVendorFreq(); // backup: the advertised speed
        }
        double cpuGhz = maxHz > 0 ? maxHz / 1_000_000_000.0 : 0;

        // --- Graphics cards (GPU) ---
        List<HardwareInfo.Gpu> gpus = new ArrayList<>();
        for (GraphicsCard card : hardware.getGraphicsCards()) {
            gpus.add(new HardwareInfo.Gpu(card.getName().trim(), card.getVRam() / (1024 * 1024)));
        }

        // --- Memory (RAM) ---
        GlobalMemory memory = hardware.getMemory();
        long totalRamMb = memory.getTotal() / (1024 * 1024);
        long freeRamMb = memory.getAvailable() / (1024 * 1024);

        // --- Monitor: resolution and refresh rate ---
        int width = 1920;
        int height = 1080;
        int refresh = 60;
        try {
            DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDisplayMode();
            width = mode.getWidth();
            height = mode.getHeight();
            if (mode.getRefreshRate() != DisplayMode.REFRESH_RATE_UNKNOWN) {
                refresh = mode.getRefreshRate();
            }
        } catch (Exception e) {
            // no monitor info available – keep the common defaults above
        }

        String os = system.getOperatingSystem().getFamily() + " "
                + system.getOperatingSystem().getVersionInfo().getVersion();

        return new HardwareInfo(cpuName, cpu.getPhysicalProcessorCount(), cpu.getLogicalProcessorCount(),
                cpuGhz, gpus, totalRamMb, freeRamMb, width, height, refresh, os.trim());
    }
}
