package com.nexusclient.legacy;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.dfr.Dfr;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import com.nexusclient.core.util.SelfTestMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

/**
 * SelfTest – a developer check for 1.8.9 (only with -Dnexus.selftest=true, see mod-legacy's build
 * file). Players never see this. Once the title screen shows it:
 *  1. opens the mod menu and HUD editor, screenshotting each
 *  2. makes a flat creative test world and joins it
 *  3. "presses" Right Shift through Minecraft's own key system and checks the mod menu opened
 *  4. screenshots the menu and the in-game HUD, then closes the game
 * Screenshots go to mod-legacy/run/screenshots.
 */
public final class SelfTest {

    private static int ticks = -1;
    private static int worldTicks = -1;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.selftest");
    }

    /** Called every tick by NexusLegacy. */
    public static void tick(MinecraftClient mc) {
        if (ticks < 0) {
            if (mc.currentScreen instanceof TitleScreen) {
                ticks = 0; // the game has finished loading – start the test
                // Don't open the pause menu if the window loses focus during the test (that would
                // block the Right Shift check, which only works with no menu open).
                mc.options.pauseOnLostFocus = false;
            }
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            log("tick " + ticks + ": screen = " + screenName(mc) + ", in world = " + (mc.world != null));
        }
        switch (ticks) {
            case 20:
                log("keys: menu = " + keyName(LegacyKeys.menu) + ", zoom = " + keyName(LegacyKeys.zoom));
                NexusCore.get().platform().openScreen(new ModMenu());
                break;
            case 60:
                screenshot(mc, "selftest-menu.png");
                break;
            case 80:
                NexusCore.get().platform().openScreen(new HudEditor());
                break;
            case 120:
                screenshot(mc, "selftest-hud-editor.png");
                break;
            case 140:
                mc.setScreen(null);
                log("making the test world");
                mc.startIntegratedServer("nexus-selftest", "Nexus self-test",
                        new LevelInfo(0L, LevelInfo.GameMode.CREATIVE, false, false, LevelGeneratorType.FLAT));
                break;
            default:
                break;
        }
        if (ticks > 140) {
            worldTest(mc);
        }
        if (ticks > 2400) {
            log("FAILED: the test world never finished loading");
            mc.scheduleStop();
        }
    }

    /** The in-world part: waits for the world, then tests the Right Shift key. */
    private static void worldTest(MinecraftClient mc) {
        if (worldTicks < 0) {
            if (mc.world != null && mc.player != null && mc.currentScreen == null) {
                worldTicks = 0;
                log("in the test world");
            }
            return;
        }
        worldTicks++;
        if (worldTicks == 40) {
            SelfTestMods.switchOn(); // midnight sky + small items, so their hooks run before the screenshot
            // An enchanted sword in hand (glint colour) and some diamonds on the ground (item physics),
            // using 1.8.9's command syntax, run by the world's built-in server.
            final net.minecraft.server.MinecraftServer server = mc.getServer();
            if (server != null) {
                server.submit(new Runnable() {
                    @Override
                    public void run() {
                        for (String command : new String[] {
                                "give @p minecraft:diamond_sword 1 0 {ench:[{id:16s,lvl:1s}]}",
                                "execute @p ~ ~ ~ summon Item ~ ~1 ~3 {Item:{id:\"minecraft:diamond\",Count:3b}}",
                                // A sidebar scoreboard, for Custom Scoreboard (1.8.9 has no boss bar command).
                                "scoreboard objectives add nexus dummy NEXUS TEST", "scoreboard objectives setdisplay sidebar nexus",
                                "scoreboard players set Kills nexus 3", "scoreboard players set Coins nexus 250"}) {
                            server.getCommandManager().execute(server, command);
                        }
                    }
                });
            }
            // Face the player (F5) with the developer badge forced on, so the screenshot shows the
            // name tag with its Nexus badge and DEV tag.
            System.setProperty("nexus.badgeTest", "DEVELOPER");
            mc.options.perspective = 1; // from behind: cape + name tag
        } else if (worldTicks == 66) {
            mc.options.perspective = 0;
        } else if (worldTicks == 69) {
            screenshot(mc, "selftest-firstperson.png"); // minimap + waypoint marker
        } else if (worldTicks == 60) {
            screenshot(mc, "selftest-hud.png");
        } else if (worldTicks == 70) {
            // The same call Minecraft makes when the player presses the key bound to the menu.
            log("pressing Right Shift (key code " + LegacyKeys.menu.getCode() + ")");
            KeyBinding.onKeyPressed(LegacyKeys.menu.getCode());
        } else if (worldTicks == 72) {
            boolean opened = mc.currentScreen instanceof NexusScreenWrapper
                    && ((NexusScreenWrapper) mc.currentScreen).screen() instanceof ModMenu;
            log(opened ? "PASSED: Right Shift opened the mod menu" : "FAILED: Right Shift didn't open the mod menu ("
                    + screenName(mc) + ")");
        } else if (worldTicks == 100) {
            screenshot(mc, "selftest-ingame-menu.png");
        } else if (worldTicks == 120) {
            // DFR: Smooth first, then Low lag, always generating (the test PC may be faster than
            // half the refresh rate, where DFR would normally rest).
            mc.setScreen(null);
            System.setProperty("nexus.dfr.force", "true");
            Dfr dfr = NexusCore.get().get(Dfr.class);
            dfr.mode.set("Creative");
            dfr.setEnabled(true);
        } else if (worldTicks == 180 || worldTicks == 240) {
            Dfr dfr = NexusCore.get().get(Dfr.class);
            log("DFR (" + (dfr.lowLag() ? "Low lag" : "Smooth") + "): real FPS " + MinecraftClient.getCurrentFps()
                    + ", generated FPS " + dfr.generatedFps() + (dfr.isEnabled() ? "" : " – SWITCHED OFF"));
            if (worldTicks == 180) {
                screenshot(mc, "selftest-dfr.png");
                dfr.mode.set("PvP");
                dfr.pvpStyle.set("Low lag");
            } else {
                dfr.setEnabled(false);
            }
        } else if (worldTicks > 245 && worldTicks < 262 && mc.player != null) {
            // Motion Blur (switched on by SelfTestMods): spin the camera so the screenshot shows a trail.
            mc.player.yaw += 12;
            if (worldTicks == 261) {
                screenshot(mc, "selftest-motionblur.png");
            }
        } else if (worldTicks >= 266 && (!com.nexusclient.core.util.SocialSelfTest.wanted()
                || com.nexusclient.core.util.SocialSelfTest.step(worldTicks - 266))) {
            log("finished");
            mc.scheduleStop();
        }
    }

    private static void screenshot(MinecraftClient mc, String name) {
        ScreenshotUtils.saveScreenshot(mc.runDirectory, name, mc.width, mc.height, mc.getFramebuffer());
        log("screenshot " + name);
    }

    private static String keyName(KeyBinding key) {
        return org.lwjgl.input.Keyboard.getKeyName(key.getCode());
    }

    private static String screenName(MinecraftClient mc) {
        if (mc.currentScreen instanceof NexusScreenWrapper) {
            return ((NexusScreenWrapper) mc.currentScreen).screen().getClass().getSimpleName();
        }
        return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
    }

    private static void log(String message) {
        System.out.println("[Nexus] self-test " + message);
    }
}
