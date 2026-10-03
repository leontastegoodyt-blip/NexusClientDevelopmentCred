package com.nexusclient.launcher.game;

import java.util.List;

/**
 * OperatingSystem – works out which kind of computer Nexus is running on (Windows, Mac or Linux,
 * 32/64-bit, Intel or ARM) and gives the names Mojang uses for each.
 *
 * Minecraft's files differ per computer: Windows needs .dll files, Mac needs .dylib files, and
 * each needs its own build of Java. Mojang's lists label everything with these names.
 */
public enum OperatingSystem {

    WINDOWS("windows"),
    MAC("osx"),
    LINUX("linux");

    private final String mojangName;

    OperatingSystem(String mojangName) {
        this.mojangName = mojangName;
    }

    /** The name Mojang uses in its files: "windows", "osx" or "linux". */
    public String mojangName() {
        return mojangName;
    }

    /** The operating system this computer is running. */
    public static OperatingSystem current() {
        String name = System.getProperty("os.name").toLowerCase();
        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return MAC;
        }
        return LINUX;
    }

    /** True on ARM processors, like Apple M1/M2/M3 Macs. */
    public static boolean isArm() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return arch.contains("aarch64") || arch.contains("arm");
    }

    /** True on 64-bit computers (almost every computer today). */
    public static boolean is64Bit() {
        return System.getProperty("os.arch").contains("64");
    }

    /** The processor name Mojang's rules use. Only "x86" (old 32-bit PCs) is ever checked. */
    public static String mojangArch() {
        if (!is64Bit()) {
            return "x86";
        }
        return isArm() ? "arm64" : "x86_64";
    }

    /**
     * The names of this computer in Mojang's Java download list, best match first.
     * The later names are backups: e.g. Mojang has no Java 8 for Apple-chip Macs, but the Intel
     * version runs fine on them through Apple's translator (Rosetta).
     */
    public List<String> javaRuntimePlatforms() {
        return switch (this) {
            case WINDOWS -> isArm() ? List.of("windows-arm64", "windows-x64")
                    : is64Bit() ? List.of("windows-x64") : List.of("windows-x86");
            case MAC -> isArm() ? List.of("mac-os-arm64", "mac-os") : List.of("mac-os");
            case LINUX -> is64Bit() ? List.of("linux") : List.of("linux-i386");
        };
    }
}
