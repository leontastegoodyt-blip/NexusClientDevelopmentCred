package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import com.nexusclient.core.util.SelfTestMods;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * SelfTest – a developer check (only with -Dnexus.selftest=true, see the version build files).
 * Players never see this. Once the title screen shows it:
 *  1. opens the mod menu and HUD editor, screenshotting each
 *  2. makes a test world and joins it
 *  3. "presses" Right Shift through Minecraft's own key system and checks the mod menu opened
 *  4. screenshots the menu and the in-game HUD, then closes the game
 * Screenshots go to run/screenshots in the version's folder.
 */
public final class SelfTest {

    private static int ticks = -1;
    private static int worldTicks = -1;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.selftest");
    }

    /** Called every tick by NexusModern. */
    public static void tick(Minecraft mc) {
        if (ticks < 0) {
            // The game has finished loading once the loading overlay is gone and a menu shows (the
            // title screen, or the accessibility screen Minecraft shows on its very first start).
            if (mc.getOverlay() == null && mc.screen != null) {
                ticks = 0;
                mc.setScreen(new TitleScreen());
                // Don't open the pause menu if the window loses focus during the test (that would
                // block the Right Shift check, which only works with no menu open).
                mc.options.pauseOnLostFocus = false;
            }
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            log("tick " + ticks + ": screen = " + screenName(mc) + ", in world = " + (mc.level != null));
        }
        switch (ticks) {
            case 20 -> {
                log("keys: menu = " + keyName(NexusModern.menuKey) + ", zoom = " + keyName(NexusModern.zoomKey));
                NexusCore.get().platform().openScreen(new ModMenu());
            }
            case 60 -> screenshot(mc, "menu");
            case 80 -> NexusCore.get().platform().openScreen(new HudEditor());
            case 120 -> screenshot(mc, "HUD editor");
            case 140 -> {
                mc.setScreen(null);
                log("making the test world");
                VersionHooks.createTestWorld(mc);
            }
            default -> { }
        }
        if (ticks > 140) {
            worldTest(mc);
        }
        if (ticks > 3600) {
            log("FAILED: the test world never finished loading");
            mc.stop();
        }
    }

    /** The in-world part: waits for the world, then tests the Right Shift key. */
    private static void worldTest(Minecraft mc) {
        if (worldTicks < 0) {
            if (mc.level != null && mc.player != null && mc.screen == null) {
                worldTicks = 0;
                log("in the test world");
            }
            return;
        }
        worldTicks++;
        if (worldTicks == 40) {
            SelfTestMods.switchOn(); // midnight sky + small items, so their hooks run before the screenshot
            // Face the player (F5) with the developer badge forced on, so the screenshot shows the
            // name tag with its Nexus badge and DEV tag.
            System.setProperty("nexus.badgeTest", "DEVELOPER");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (worldTicks == 66) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else if (worldTicks == 60) {
            screenshot(mc, "in-game HUD");
        } else if (worldTicks == 70) {
            // The same call Minecraft makes when the player presses the key bound to the menu.
            log("pressing " + keyName(NexusModern.menuKey));
            KeyMapping.click(KeyBindingHelper.getBoundKeyOf(NexusModern.menuKey));
        } else if (worldTicks == 72) {
            boolean opened = mc.screen instanceof NexusScreenWrapper wrapper && wrapper.screen() instanceof ModMenu;
            log(opened ? "PASSED: Right Shift opened the mod menu"
                    : "FAILED: Right Shift didn't open the mod menu (" + screenName(mc) + ")");
        } else if (worldTicks == 100) {
            screenshot(mc, "in-game menu");
        } else if (worldTicks == 120) {
            log("finished");
            mc.stop();
        }
    }

    private static void screenshot(Minecraft mc, String what) {
        Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> log("screenshot of the " + what
                + ": " + message.getString()));
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static String screenName(Minecraft mc) {
        if (mc.screen instanceof NexusScreenWrapper wrapper) {
            return wrapper.screen().getClass().getSimpleName();
        }
        return mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
    }

    private static void log(String message) {
        System.out.println("[Nexus] self-test " + message);
    }
}
