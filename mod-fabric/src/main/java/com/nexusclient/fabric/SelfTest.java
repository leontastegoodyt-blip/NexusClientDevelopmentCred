package com.nexusclient.fabric;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * SelfTest – a developer check (only when started with -Dnexus.selftest=true, see mod-fabric's
 * build file). Once the title screen shows, it opens the mod menu and HUD editor by itself, takes
 * a screenshot of each, then closes the game. Players never see this.
 */
public final class SelfTest {

    private static int ticks = -1;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.selftest");
    }

    /** Called every tick by NexusFabric. */
    public static void tick(Minecraft mc) {
        if (ticks < 0) {
            if (mc.gui.screen() instanceof TitleScreen) {
                ticks = 0; // the game has finished loading – start the test
            }
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            Object screen = mc.gui.screen();
            System.out.println("[Nexus] self-test tick " + ticks + ": screen = "
                    + (screen instanceof NexusScreenWrapper w ? w.screen().getClass().getSimpleName()
                    : screen == null ? "none" : screen.getClass().getSimpleName()));
        }
        com.nexusclient.core.dfr.Dfr dfr = NexusCore.get().get(com.nexusclient.core.dfr.Dfr.class);
        if (ticks == 10) {
            dfr.mode.set("Creative"); // Smooth blending
            dfr.setEnabled(true);
        }
        if (ticks == 60 || ticks == 130) {
            System.out.println("[Nexus] self-test DFR (" + (dfr.lowLag() ? "Low lag" : "Smooth") + "): real FPS "
                    + mc.getFps() + ", generated FPS " + dfr.generatedFps());
        }
        if (ticks == 70) {
            dfr.mode.set("PvP");
            dfr.pvpStyle.set("Low lag");
        }
        if (ticks == 140) {
            dfr.setEnabled(false);
        }
        switch (ticks) {
            case 100 -> {
                // Check the keys are really bound to Right Shift and C (Minecraft's own key names).
                System.out.println("[Nexus] self-test keys: menu = " + NexusFabric.menuKey.getTranslatedKeyMessage().getString()
                        + ", zoom = " + NexusFabric.zoomKey.getTranslatedKeyMessage().getString());
                NexusCore.get().platform().openScreen(new ModMenu());
            }
            case 160 -> Screenshot.grab(mc, false);
            case 180 -> NexusCore.get().platform().openScreen(new HudEditor());
            case 240 -> Screenshot.grab(mc, false);
            case 280 -> {
                System.out.println("[Nexus] self-test finished");
                mc.stop();
            }
            default -> { }
        }
    }
}
