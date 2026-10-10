package com.nexusclient.core.module;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.FrameTimes;
import com.nexusclient.core.hud.TextHudModule;
import com.nexusclient.core.online.Badges;
import com.nexusclient.core.online.DeveloperMode;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * DevTools – the in-game developer tools, in the "Developer" category of the Right Shift menu.
 * Only the Nexus developer sees them (online/DeveloperMode asks the Nexus server). They only SHOW
 * information or change what you see on your own screen – nothing here affects other players.
 *
 *   Debug HUD      – frame times, memory, server + ping, Nexus server status, badges, server rules
 *   Badge Preview  – shows every player with a chosen badge (to check how badges look)
 *   Copy Position  – a button-like mod: switch it on to copy "x y z" to the clipboard
 */
public final class DevTools {

    private DevTools() {}

    public static List<com.nexusclient.core.hud.HudModule> hudModules() {
        return new ArrayList<com.nexusclient.core.hud.HudModule>(Arrays.asList(new DebugHud()));
    }

    public static List<Module> modules() {
        return new ArrayList<Module>(Arrays.<Module>asList(new BadgePreview(), new CopyPosition()));
    }

    /** A box of numbers that help while building and testing Nexus. */
    public static class DebugHud extends TextHudModule {
        public final BooleanSetting showRules = add(new BooleanSetting("showRules", "Show blocked mods", true));

        public DebugHud() {
            super("devDebugHud", "Debug HUD", "Frame times, memory, server, Nexus status, badges and rules.",
                    Category.DEVELOPER, false, 0.70, 0.02);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            List<String> lines = new ArrayList<String>();
            lines.add("NEXUS DEV");
            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            float[] frames = FrameTimes.snapshot();
            lines.add(String.format(Locale.ROOT, "%d fps  ·  avg %.1f ms  ·  1%% low %d",
                    preview ? 144 : game.fps(), FrameTimes.average(frames), FrameTimes.onePercentLowFps(frames)));
            lines.add("Memory " + usedMb + " / " + runtime.maxMemory() / (1024 * 1024) + " MB");
            lines.add((preview ? "mc.hypixel.net" : game.serverAddress()) + "  ·  " + (preview ? 42 : game.ping()) + " ms");
            String backend = System.getenv("NEXUS_BACKEND");
            lines.add("Nexus server: " + (backend == null ? "none" : backend.replace("http://", ""))
                    + (System.getenv("NEXUS_TOKEN") == null ? " (signed out)" : ""));
            lines.add("Badges known: " + Badges.known() + "  ·  developer: " + (DeveloperMode.active() ? "yes" : "no"));
            if (showRules.isOn()) {
                List<String> blocked = new ArrayList<String>();
                for (Module module : NexusCore.get().modules()) {
                    if (module.isBlockedByServer()) {
                        blocked.add(module.id());
                    }
                }
                lines.add("Blocked here: " + (blocked.isEmpty() ? "nothing" : join(blocked)));
            }
            return lines;
        }

        private static String join(List<String> items) {
            StringBuilder text = new StringBuilder();
            for (String item : items) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(item);
            }
            return text.toString();
        }
    }

    /** Gives every player the chosen badge on YOUR screen, to check how badges look in name tags. */
    public static class BadgePreview extends Module {
        public final ChoiceSetting badge = add(new ChoiceSetting("badge", "Badge", "Developer",
                "Nexus", "Premium", "Developer"));

        public BadgePreview() {
            super("devBadgePreview", "Badge Preview", "Shows everyone with a chosen badge (only on your screen).",
                    Category.DEVELOPER, false, false);
        }

        @Override
        public void onTick(GameInfo game) {
            System.setProperty("nexus.badgeTest", badge.get().toUpperCase(Locale.ROOT));
        }

        @Override
        protected void onDisable() {
            System.clearProperty("nexus.badgeTest");
        }
    }

    /** Switch on → copies your position ("x y z") to the clipboard, then switches itself off. */
    public static class CopyPosition extends Module {
        public CopyPosition() {
            super("devCopyPosition", "Copy Position", "Copies your x y z to the clipboard.", Category.DEVELOPER,
                    false, false);
        }

        @Override
        public void onTick(GameInfo game) {
            if (game.inWorld()) {
                String text = String.format(Locale.ROOT, "%.2f %.2f %.2f", game.x(), game.y(), game.z());
                NexusCore.get().platform().copyToClipboard(text);
                NexusCore.get().platform().showNotice("Copied " + text);
            }
            setEnabled(false);
        }
    }
}
