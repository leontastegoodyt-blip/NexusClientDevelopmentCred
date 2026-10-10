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
        // The developer tools (normally only for the Nexus developer's account).
        System.setProperty("nexus.devTools", "true");
        core.get(com.nexusclient.core.module.DevTools.DebugHud.class).setEnabled(true);
    }
}
