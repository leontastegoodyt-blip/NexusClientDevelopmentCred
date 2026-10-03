package com.nexusclient.launcher.game;

/**
 * QuickPlay – where the game should go straight to when it starts, instead of the title screen:
 * a server (one-click join) or a singleplayer world (continue where you left off).
 *
 * @param server a server address like "mc.hypixel.net" or "play.example.com:25566" (or null)
 * @param world  a singleplayer world's folder name (or null)
 */
public record QuickPlay(String server, String world) {

    public static QuickPlay joinServer(String address) {
        return new QuickPlay(address, null);
    }

    public static QuickPlay openWorld(String worldFolder) {
        return new QuickPlay(null, worldFolder);
    }

    /**
     * Minecraft 1.20 and newer can open a world or join a server straight away ("Quick Play").
     * Older versions can only join a server (using --server and --port).
     */
    public static boolean supportsWorlds(String versionId) {
        if (!versionId.startsWith("1.")) {
            return true; // 26.x and later
        }
        try {
            return Integer.parseInt(versionId.split("\\.")[1]) >= 20;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
