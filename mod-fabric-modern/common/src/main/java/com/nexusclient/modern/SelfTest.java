package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import com.nexusclient.core.util.SelfTestMods;
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

    /** Called every tick by ModernGame. */
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
                log("keys: menu = " + keyName(ModernGame.menuKey) + ", zoom = " + keyName(ModernGame.zoomKey));
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
            // An enchanted sword in hand (glint colour) and some diamonds on the ground (item physics).
            // Both ways of writing the item are sent: older versions use "Count:3b", newer "count:3".
            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    for (String command : new String[] {"give @p minecraft:diamond_sword", "enchant @p minecraft:sharpness 1",
                            "execute at @p rotated as @p run summon item ^ ^1 ^3 {Item:{id:\"minecraft:diamond\",count:3}}",
                            "execute at @p rotated as @p run summon item ^ ^1 ^3 {Item:{id:\"minecraft:diamond\",Count:3b}}",
                            // A sidebar scoreboard and a boss bar, for Custom Scoreboard / Custom Boss Bar.
                            "scoreboard objectives add nexus dummy \"NEXUS TEST\"", "scoreboard objectives setdisplay sidebar nexus",
                            "scoreboard players set Kills nexus 3", "scoreboard players set Coins nexus 250",
                            "scoreboard players set Wins nexus 7", "bossbar add nexus:test \"Event ends soon\"",
                            "bossbar set nexus:test players @a", "bossbar set nexus:test value 60",
                            "bossbar set nexus:test color purple"}) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                    }
                });
            }
            // Face the player (F5) with the developer badge forced on, so the screenshot shows the
            // name tag with its Nexus badge and DEV tag.
            System.setProperty("nexus.badgeTest", "DEVELOPER");
            System.setProperty("nexus.clanTagTest", "TEST"); // and a clan tag after the name
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); // from behind: cape + name tag
        } else if (worldTicks == 50) {
            com.nexusclient.core.NexusCore.get().emoteKey(); // Wave (Emotes), shown from the front at noon
            com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.module.Mods.TimeChanger.class).time.set(6000.0);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (worldTicks == 56) {
            screenshot(mc, "emote");
            com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.module.Mods.TimeChanger.class).time.set(18000.0);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else if (worldTicks == 66) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else if (worldTicks == 69) {
            screenshot(mc, "first person"); // minimap + waypoint marker
        } else if (worldTicks == 60) {
            screenshot(mc, "in-game HUD");
        } else if (worldTicks == 70) {
            // The same call Minecraft makes when the player presses the key bound to the menu.
            log("pressing " + keyName(ModernGame.menuKey));
            ModernGame.click(ModernGame.menuKey);
        } else if (worldTicks == 72) {
            boolean opened = mc.screen instanceof NexusScreenWrapper wrapper && wrapper.screen() instanceof ModMenu;
            log(opened ? "PASSED: Right Shift opened the mod menu"
                    : "FAILED: Right Shift didn't open the mod menu (" + screenName(mc) + ")");
        } else if (worldTicks == 100) {
            screenshot(mc, "in-game menu");
        } else if (worldTicks == 104) {
            // A mod's settings page, for the screenshot below.
            NexusCore.get().platform().openScreen(com.nexusclient.core.ui.ModMenu.forModule(NexusCore.get().module("crosshair")));
        } else if (worldTicks == 114) {
            screenshot(mc, "settings page");
        } else if (worldTicks == 120) {
            // DFR: Smooth first, then Low lag, always generating (the test PC may be faster than
            // half the refresh rate, where DFR would normally rest).
            mc.setScreen(null);
            System.setProperty("nexus.dfr.force", "true");
            com.nexusclient.core.dfr.Dfr dfr = NexusCore.get().get(com.nexusclient.core.dfr.Dfr.class);
            dfr.mode.set("Creative");
            dfr.setEnabled(true);
        } else if (worldTicks == 180 || worldTicks == 240) {
            com.nexusclient.core.dfr.Dfr dfr = NexusCore.get().get(com.nexusclient.core.dfr.Dfr.class);
            log("DFR (" + (dfr.lowLag() ? "Low lag" : "Smooth") + "): real FPS " + mc.getFps()
                    + ", generated FPS " + dfr.generatedFps() + (dfr.isEnabled() ? "" : " – SWITCHED OFF"));
            if (worldTicks == 180) {
                screenshot(mc, "DFR smooth");
                dfr.mode.set("PvP");
                dfr.pvpStyle.set("Low lag");
            } else {
                dfr.setEnabled(false);
            }
        } else if (worldTicks > 245 && worldTicks < 262 && mc.player != null) {
            // Motion Blur (switched on by SelfTestMods): spin the camera so the screenshot shows a trail.
            mc.player.setYRot(mc.player.getYRot() + 12);
            if (worldTicks == 261) {
                screenshot(mc, "motion blur");
            }
        } else if (worldTicks == 266 && System.getenv("NEXUS_TOKEN") == null) {
            log("finished"); // no Nexus sign-in: skip the Nexus Social part
            mc.stop();
        } else if (worldTicks >= 266) {
            socialTest(mc);
        }
    }

    /**
     * Nexus Social (only when started with a Nexus sign-in, e.g. against a test server): invites the
     * first online friend to a party, sends a party message, presses the party ping key, and
     * screenshots the Social tab and the world (chat lines, ping marker, pop-ups).
     */
    private static void socialTest(Minecraft mc) {
        com.nexusclient.core.online.Social social = NexusCore.get().get(com.nexusclient.core.online.Social.class);
        if (worldTicks == 266) {
            log("social: " + social.status() + ", friends " + social.friends().size());
            for (com.nexusclient.core.online.Social.Person friend : social.friends()) {
                if (friend.online) {
                    log("social: inviting " + friend.name);
                    social.inviteToParty(friend);
                    break;
                }
            }
        } else if (worldTicks == 330) {
            log("social: party of " + social.partyMembers().size() + ", " + social.partyChat().size() + " party messages");
            log("social: chat typed @p → handled = " + NexusCore.get().chatInput("@p hi from the game"));
        } else if (worldTicks == 340) {
            NexusCore.get().partyPingKey();
        } else if (worldTicks == 374) {
            com.nexusclient.core.ui.Toasts.show("PartyPal is online", "Playing on mc.hypixel.net", "F", 0x5BE49B);
            com.nexusclient.core.ui.Toasts.show("Party invite", "PartyPal · accept in Right Shift → Social", "P", 0xB98CFF);
        } else if (worldTicks == 380) {
            screenshot(mc, "social world");
            NexusCore.get().platform().openScreen(ModMenu.social());
        } else if (worldTicks == 400) {
            screenshot(mc, "social tab");
        } else if (worldTicks == 410) {
            log("social: party messages " + social.partyChat().size() + ", clan " + social.clanTag());
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
