package com.nexusclient.launcher;

/**
 * DevMode – true only while running Nexus from source with `gradlew :launcher:run`
 * (launcher/build.gradle.kts turns it on). Real releases never have it.
 *
 * It exists so the game can be tested before Mojang approves Nexus's Microsoft login.
 */
public final class DevMode {

    private DevMode() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.dev");
    }
}
