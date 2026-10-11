package com.nexusclient.core.util;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;

/**
 * SelfTestMods – used only by the developer self-tests (never by players). Switches on a few mods
 * whose hooks into the game should be exercised, so a broken hook shows up in the test (as a
 * crash or a wrong-looking screenshot) instead of in a player's game.
 */
public final class SelfTestMods {

    private SelfTestMods() {}

    /** Midnight sky (Time Changer), smaller held items, lower fire, and the colour/outline mods. */
    public static void switchOn() {
        NexusCore core = NexusCore.get();
        Mods.TimeChanger time = core.get(Mods.TimeChanger.class);
        time.time.set(18000.0); // midnight – easy to see in the screenshot
        time.setEnabled(true);
        core.get(Mods.SmallItems.class).setEnabled(true);
        core.get(Mods.LowFire.class).setEnabled(true);
        core.get(Mods.CopyChat.class).setEnabled(true);
        core.get(Mods.HitColor.class).setEnabled(true);
        core.get(Mods.BlockOutline.class).setEnabled(true);
        core.get(Mods.Hitboxes.class).setEnabled(true);
        core.get(Mods.Particles.class).setEnabled(true);
        core.get(Mods.Nametags.class).setEnabled(true);
        core.get(Mods.ItemPhysics.class).setEnabled(true);
        core.get(Mods.EnchantGlint.class).setEnabled(true);
        core.get(Mods.MotionBlur.class).setEnabled(true);
        // Everyone wears the Nexus test cape (the picture from the backend folder, when running from the project).
        for (java.io.File folder = new java.io.File(".").getAbsoluteFile(); folder != null; folder = folder.getParentFile()) {
            java.io.File cape = new java.io.File(folder, "backend/src/main/resources/static/cosmetics/nexus_cape.png");
            if (cape.isFile()) {
                System.setProperty("nexus.capeTest", cape.getAbsolutePath());
                break;
            }
        }
        // The developer tools (normally only for the Nexus developer's account).
        System.setProperty("nexus.devTools", "true");
        core.get(com.nexusclient.core.module.DevTools.DebugHud.class).setEnabled(true);
        // Minimap + a test waypoint 10 blocks in front of the player (so both show in the screenshot).
        core.get(com.nexusclient.core.map.Minimap.class).setEnabled(true);
        com.nexusclient.core.map.Waypoints waypoints = core.get(com.nexusclient.core.map.Waypoints.class);
        waypoints.setEnabled(true);
        com.nexusclient.core.platform.GameInfo game = core.game();
        double yaw = Math.toRadians(game.yaw());
        waypoints.store().add(game.worldKey(), game.dimension(), "Test waypoint",
                (int) Math.floor(game.x() - Math.sin(yaw) * 10), (int) Math.floor(game.y()),
                (int) Math.floor(game.z() + Math.cos(yaw) * 10));
    }
}
