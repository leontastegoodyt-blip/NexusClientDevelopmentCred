package com.nexusclient.core.hud;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.GameInfo.EffectInfo;
import com.nexusclient.core.platform.GameInfo.ItemInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;
import com.nexusclient.core.settings.ColorSetting;
import com.nexusclient.core.settings.NumberSetting;
import com.nexusclient.core.settings.TextSetting;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HudMods – every HUD mod from the project brief (section 4, HUD mods), each as a small class.
 *
 * Positions are fractions of the screen (0.0–1.0). Most start in the top-left column, stacked, so
 * a new player sees a tidy HUD; they can drag them anywhere in the HUD editor.
 */
public final class HudMods {

    private HudMods() {}

    /** All HUD mods, in the order they appear in the mod menu. */
    public static List<HudModule> all() {
        return new ArrayList<HudModule>(Arrays.<HudModule>asList(
                new Fps(), new Cps(), new Ping(), new Keystrokes(), new Coordinates(), new Direction(),
                new Biome(), new ArmorHud(), new HeldItems(), new PotionEffects(), new ItemCounter(),
                new Clock(), new Memory(), new ServerAddress(), new Combo(), new SpeedrunTimer(),
                new PackDisplay(), new PvpStats(), new FrameTimeGraph(), new com.nexusclient.core.map.Minimap(),
                new NowPlaying(), new CustomScoreboard(), new CustomBossBar()));
    }

    // ================================================================== simple text

    /** Frames per second. */
    public static class Fps extends TextHudModule {
        public Fps() {
            super("fps", "FPS", "Shows your frames per second.", true, 0.005, 0.01);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            // With DFR on: "72 FPS + 72 DFR" (real frames + generated frames).
            com.nexusclient.core.dfr.Dfr dfr = com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.dfr.Dfr.class);
            if (dfr.isActive() && dfr.showInFps.isOn()) {
                int generated = preview ? 72 : dfr.generatedFps();
                if (generated == 0) {
                    // DFR rests when the game is already fast enough for the screen (extra frames
                    // couldn't be shown) – say so, instead of a confusing "+ 0 DFR".
                    return one(game.fps() + " FPS  (DFR resting – already smooth)");
                }
                return one((preview ? 72 : game.fps()) + " FPS + " + generated + " DFR");
            }
            return one((preview ? 144 : game.fps()) + " FPS");
        }
    }

    /** Clicks per second, left | right. */
    public static class Cps extends TextHudModule {
        public final BooleanSetting showRight = add(new BooleanSetting("showRight", "Show right clicks", true));

        public Cps() {
            super("cps", "CPS", "Shows your clicks per second.", true, 0.005, 0.08);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            int left = preview ? 9 : InputTracker.cps(0);
            int right = preview ? 4 : InputTracker.cps(1);
            return one(showRight.isOn() ? left + " | " + right + " CPS" : left + " CPS");
        }
    }

    /** Ping to the server. */
    public static class Ping extends TextHudModule {
        public Ping() {
            super("ping", "Ping", "Shows your connection delay to the server.", false, 0.005, 0.15);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            return one((preview ? 42 : game.ping()) + " ms");
        }
    }

    /** X / Y / Z and the direction you face. */
    public static class Coordinates extends TextHudModule {
        public final BooleanSetting showFacing = add(new BooleanSetting("showFacing", "Show facing", true));

        public Coordinates() {
            super("coordinates", "Coordinates", "Shows where you are (X, Y, Z).", true, 0.005, 0.22);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            List<String> lines = new ArrayList<String>();
            if (!preview && NexusCore.get().get(Mods.StreamerMode.class).hidesCoordinates()) {
                lines.add("Coordinates hidden"); // Streamer Mode is on
                return lines;
            }
            double x = preview ? 128.5 : game.x();
            double y = preview ? 64 : game.y();
            double z = preview ? -302.2 : game.z();
            lines.add("X: " + (int) Math.floor(x));
            lines.add("Y: " + (int) Math.floor(y));
            lines.add("Z: " + (int) Math.floor(z));
            if (showFacing.isOn()) {
                lines.add("Facing: " + facing(preview ? 45 : game.yaw()));
            }
            return lines;
        }
    }

    /** The biome you're standing in. */
    public static class Biome extends TextHudModule {
        public Biome() {
            super("biome", "Biome", "Shows the biome you're in.", false, 0.005, 0.43);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            return one(preview ? "Plains" : game.biome());
        }
    }

    /** Real time or in-game time. */
    public static class Clock extends TextHudModule {
        public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Time", "Real time", "Real time", "In-game time"));
        public final BooleanSetting twentyFour = add(new BooleanSetting("24h", "24-hour clock", true));

        public Clock() {
            super("clock", "Clock", "Shows the time.", false, 0.93, 0.01);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            if (mode.is("In-game time")) {
                long ticks = preview ? 6000 : game.dayTime() % 24000;
                int hours = (int) ((ticks / 1000 + 6) % 24); // tick 0 = 6:00
                int minutes = (int) (ticks % 1000 * 60 / 1000);
                return one(String.format(Locale.ROOT, "%02d:%02d", hours, minutes));
            }
            return one(new SimpleDateFormat(twentyFour.isOn() ? "HH:mm" : "h:mm a").format(new Date()));
        }
    }

    /** How much memory the game uses. */
    public static class Memory extends TextHudModule {
        public Memory() {
            super("memory", "Memory", "Shows how much memory (RAM) the game is using.", false, 0.005, 0.50);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            long max = runtime.maxMemory();
            return one(String.format(Locale.ROOT, "Mem: %d%% (%d / %d MB)", used * 100 / max,
                    used / (1024 * 1024), max / (1024 * 1024)));
        }
    }

    /** The server you're on. */
    public static class ServerAddress extends TextHudModule {
        public ServerAddress() {
            super("serverAddress", "Server Address", "Shows which server you're playing on.", false, 0.005, 0.57);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            if (!preview && NexusCore.get().get(Mods.StreamerMode.class).hidesServerAddresses()) {
                return one("Server hidden"); // Streamer Mode is on
            }
            return one(preview ? "mc.hypixel.net" : game.serverAddress());
        }
    }

    /** Hits in a row. */
    public static class Combo extends TextHudModule {
        public Combo() {
            super("combo", "Combo", "Counts your hits in a row without being hit back.", false, 0.2, 0.01);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            int combo = preview ? 5 : InputTracker.combo();
            return one(combo == 0 ? "No combo" : combo + " combo");
        }
    }

    /** PvP stats for this session: kills, deaths, K/D, streak and wins (see SessionStats). */
    public static class PvpStats extends TextHudModule {
        public final BooleanSetting showWins = add(new BooleanSetting("showWins", "Show wins", true));
        public final TextSetting winMessages = add(new TextSetting("winMessages",
                "Chat messages that mean you won (comma separated)", "VICTORY!,You won", 200));

        public PvpStats() {
            super("pvpStats", "PvP Stats", "Kills, deaths, K/D, kill streak and wins for this session.", false,
                    0.2, 0.15);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            List<String> lines = new ArrayList<String>();
            if (preview) {
                lines.add("Kills 7  Deaths 2  K/D 3.50");
                lines.add("Streak 3  (best 5)");
                if (showWins.isOn()) lines.add("Wins 2");
                return lines;
            }
            lines.add("Kills " + SessionStats.kills() + "  Deaths " + SessionStats.deaths() + "  K/D " + SessionStats.kd());
            lines.add("Streak " + SessionStats.streak() + "  (best " + SessionStats.bestStreak() + ")");
            if (showWins.isOn()) lines.add("Wins " + SessionStats.wins());
            return lines;
        }
    }

    /**
     * Frame-time graph: one bar per frame, taller = that frame took longer. A flat, low graph means
     * smooth play; tall spikes are stutters. Also shows the average and the "1% low" FPS, and the
     * TorcherRig tier the launcher set (DFR's generated frames will show here in step 16).
     */
    public static class FrameTimeGraph extends HudModule {
        private static final int WIDTH = 120;
        private static final int GRAPH_HEIGHT = 32;
        /** Frame time (ms) at the very top of the graph. 50 ms = 20 FPS. */
        private static final float TOP_MS = 50f;

        public FrameTimeGraph() {
            super("frameTimeGraph", "Frame-time Graph", "Shows how smooth the game is running, frame by frame.",
                    false, 0.78, 0.3);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            float[] frames = preview ? exampleFrames() : FrameTimes.snapshot();
            int textHeight = r.lineHeight() + 1;
            String tier = System.getProperty("nexus.torcherTier");
            int lines = tier == null ? 1 : 2;
            int height = GRAPH_HEIGHT + textHeight * lines + 6;
            drawBackground(r, WIDTH, height);

            // Guide lines at 60 FPS (16.7 ms) and 30 FPS (33.3 ms).
            int base = 3 + GRAPH_HEIGHT;
            r.rect(3, base - barHeight(16.7f), WIDTH - 3, base - barHeight(16.7f) + 1, 0x303AA8FF);
            r.rect(3, base - barHeight(33.3f), WIDTH - 3, base - barHeight(33.3f) + 1, 0x30FFB547);

            // One bar per frame, newest on the right; one pixel wide, so only the newest fit.
            int bars = Math.min(frames.length, WIDTH - 6);
            for (int i = 0; i < bars; i++) {
                float ms = frames[frames.length - bars + i];
                int x = 3 + i;
                int colour = ms <= 16.7f ? 0xFF3DDC97 : ms <= 33.3f ? 0xFFFFB547 : 0xFFFF4D6D; // green / amber / red
                r.rect(x, base - barHeight(ms), x + 1, base, colour);
            }

            float avg = FrameTimes.average(frames);
            String stats = String.format(Locale.ROOT, "%.1f ms  ·  1%% low %d FPS", avg, FrameTimes.onePercentLowFps(frames));
            drawText(r, stats, 3, base + 3);
            if (tier != null) {
                drawText(r, "TorcherRig: " + tier, 3, base + 3 + textHeight);
            }
            return new int[] {WIDTH, height};
        }

        private static int barHeight(float ms) {
            return Math.max(1, Math.round(Math.min(ms, TOP_MS) / TOP_MS * GRAPH_HEIGHT));
        }

        /** Made-up frames for the HUD editor: mostly smooth with a couple of spikes. */
        private static float[] exampleFrames() {
            float[] frames = new float[FrameTimes.SIZE];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = 7f + (float) Math.sin(i * 0.3) * 1.5f + (i % 57 == 0 ? 26f : 0) + (i % 91 == 0 ? 12f : 0);
            }
            return frames;
        }
    }

    /** Which resource packs are on. */
    public static class PackDisplay extends TextHudModule {
        public PackDisplay() {
            super("packDisplay", "Pack Display", "Shows the resource pack you're using.", false, 0.2, 0.08);
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            List<String> packs = preview ? Arrays.asList("Fresh Animations") : game.resourcePacks();
            return one(packs.isEmpty() ? "Default textures" : "Pack: " + packs.get(0));
        }
    }

    /**
     * A speedrun timer with splits. The loader module calls start/split/reset from key presses
     * (by default: the timer starts when a world loads).
     */
    public static class SpeedrunTimer extends TextHudModule {
        private long startMillis;
        private final List<Long> splits = new ArrayList<Long>();
        public final BooleanSetting autoStart = add(new BooleanSetting("autoStart", "Start when a world loads", true));

        public SpeedrunTimer() {
            super("speedrunTimer", "Speedrun Timer", "A timer with splits for speedruns.", false, 0.84, 0.05);
        }

        public void start() {
            startMillis = System.currentTimeMillis();
            splits.clear();
        }

        public void split() {
            if (startMillis > 0) {
                splits.add(System.currentTimeMillis() - startMillis);
            }
        }

        public void reset() {
            startMillis = 0;
            splits.clear();
        }

        public boolean running() {
            return startMillis > 0;
        }

        @Override
        protected List<String> lines(GameInfo game, boolean preview) {
            List<String> lines = new ArrayList<String>();
            long elapsed = preview ? 754_300 : (startMillis == 0 ? 0 : System.currentTimeMillis() - startMillis);
            lines.add(time(elapsed));
            List<Long> shown = preview ? Arrays.asList(123_400L, 456_700L) : splits;
            for (int i = 0; i < shown.size(); i++) {
                lines.add("Split " + (i + 1) + ": " + time(shown.get(i)));
            }
            return lines;
        }

        private static String time(long millis) {
            return String.format(Locale.ROOT, "%d:%02d.%d", millis / 60000, millis / 1000 % 60, millis / 100 % 10);
        }
    }

    // ================================================================== drawn by hand

    /** WASD, jump and mouse buttons, lighting up when pressed. */
    public static class Keystrokes extends HudModule {
        public final BooleanSetting showMouse = add(new BooleanSetting("showMouse", "Show mouse buttons", true));
        public final BooleanSetting showSpace = add(new BooleanSetting("showSpace", "Show jump (space)", true));
        public final ColorSetting pressedColor = add(new ColorSetting("pressedColor", "Pressed colour", 0xB03AA8FF));

        public Keystrokes() {
            super("keystrokes", "Keystrokes", "Shows which movement keys and mouse buttons you're pressing.",
                    true, 0.005, 0.72);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            int size = 20;
            int gap = 2;
            key(r, "W", size + gap, 0, size, size, down(game, "forward", preview));
            key(r, "A", 0, size + gap, size, size, down(game, "left", preview));
            key(r, "S", size + gap, size + gap, size, size, down(game, "back", preview));
            key(r, "D", (size + gap) * 2, size + gap, size, size, down(game, "right", preview));
            int y = (size + gap) * 2;
            int width = size * 3 + gap * 2;
            if (showMouse.isOn()) {
                int half = (width - gap) / 2;
                key(r, "LMB " + InputTracker.cps(0), 0, y, half, size, down(game, "attack", preview));
                key(r, "RMB " + InputTracker.cps(1), half + gap, y, half, size, down(game, "use", preview));
                y += size + gap;
            }
            if (showSpace.isOn()) {
                key(r, "———", 0, y, width, size / 2 + 2, down(game, "jump", preview));
                y += size / 2 + 2 + gap;
            }
            return new int[] {width, y - gap};
        }

        private boolean down(GameInfo game, String action, boolean preview) {
            return preview ? action.equals("forward") : game.isKeyDown(action);
        }

        private void key(Renderer r, String label, int x, int y, int w, int h, boolean pressed) {
            r.rect(x, y, x + w, y + h, pressed ? pressedColor.argb() : backgroundColor.argb());
            int tx = x + (w - r.textWidth(label)) / 2;
            int ty = y + (h - r.lineHeight()) / 2 + 1;
            r.text(label, tx, ty, pressed ? 0xFF101018 : textColor.argbAt(x), textShadow.isOn() && !pressed);
        }
    }

    /** A compass strip showing N/E/S/W around the direction you face. */
    public static class Direction extends HudModule {
        public Direction() {
            super("direction", "Direction", "A compass showing which way you're facing.", false, 0.42, 0.01);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            int width = 120;
            int height = r.lineHeight() + 8;
            drawBackground(r, width, height);
            float yaw = preview ? 30 : game.yaw();
            String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            r.enableClip(0, 0, width, height); // only draw inside the strip
            for (int i = -8; i <= 16; i++) {
                float angle = i * 45f;
                float diff = angle - ((yaw % 360) + 360) % 360;
                int px = Math.round(width / 2f + diff * 1.2f);
                if (px < -10 || px > width + 10) continue;
                String name = names[((i % 8) + 8) % 8];
                drawText(r, name, px - r.textWidth(name) / 2, 4);
            }
            r.disableClip();
            r.rect(width / 2, height - 3, width / 2 + 1, height, textColor.argb()); // centre marker
            return new int[] {width, height};
        }
    }

    /** Armour pieces with their durability. */
    public static class ArmorHud extends HudModule {
        public final BooleanSetting showDurability = add(new BooleanSetting("durability", "Show durability", true));

        public ArmorHud() {
            super("armorHud", "Armour HUD", "Shows your armour and how worn out it is.", false, 0.9, 0.7);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            List<ItemInfo> armor = game.armor();
            if (armor.isEmpty() && preview) {
                return exampleBox(r, "Armour", 4);
            }
            return itemList(r, armor);
        }
    }

    /** Main hand + off hand (totem, shield, weapon) with counts/durability. */
    public static class HeldItems extends HudModule {
        public HeldItems() {
            super("heldItems", "Totem / Shield / Weapon", "Shows what you're holding in each hand.", false, 0.9, 0.6);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            List<ItemInfo> held = game.heldItems();
            if (held.isEmpty() && preview) {
                return exampleBox(r, "Held items", 2);
            }
            return itemList(r, held);
        }
    }

    /** Active potion effects and time left. */
    public static class PotionEffects extends HudModule {
        public PotionEffects() {
            super("potionEffects", "Potion Effects", "Shows your potion effects and how long they last.",
                    false, 0.005, 0.47);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            List<String> lines = new ArrayList<String>();
            List<EffectInfo> effects = preview && game.effects().isEmpty()
                    ? Arrays.asList(new EffectInfo("Speed", 1, 2400), new EffectInfo("Strength", 0, 900))
                    : game.effects();
            for (EffectInfo effect : effects) {
                String level = effect.amplifier > 0 ? " " + roman(effect.amplifier + 1) : "";
                String time = effect.durationTicks < 0 ? "∞"
                        : String.format(Locale.ROOT, "%d:%02d", effect.durationTicks / 1200, effect.durationTicks / 20 % 60);
                lines.add(effect.name + level + "  " + time);
            }
            int width = 0;
            for (String line : lines) width = Math.max(width, r.textWidth(line));
            int height = lines.size() * (r.lineHeight() + 1) + 7;
            if (lines.isEmpty()) return new int[] {0, 0};
            drawBackground(r, width + 8, height);
            for (int i = 0; i < lines.size(); i++) drawText(r, lines.get(i), 4, 4 + i * (r.lineHeight() + 1));
            return new int[] {width + 8, height};
        }
    }

    /** How many arrows, ender pearls, golden apples and totems you have. */
    public static class ItemCounter extends HudModule {
        private static final String[] ITEMS = {"minecraft:arrow", "minecraft:ender_pearl", "minecraft:golden_apple",
                "minecraft:totem_of_undying"};

        public ItemCounter() {
            super("itemCounter", "Item Counter", "Counts your arrows, pearls, golden apples and totems.",
                    false, 0.9, 0.5);
        }

        @Override
        protected int[] draw(Renderer r, GameInfo game, boolean preview) {
            int y = 0;
            int width = 0;
            for (String id : ITEMS) {
                int count = preview && !game.inWorld() ? 16 : game.countItem(id);
                if (count == 0 && !preview) continue;
                Object icon = game.itemIcon(id);
                if (icon != null) r.item(icon, 0, y);
                String text = String.valueOf(count);
                drawText(r, text, 20, y + 4);
                width = Math.max(width, 20 + r.textWidth(text));
                y += 18;
            }
            return new int[] {Math.max(width, 20), Math.max(y, 18)};
        }
    }

    // ================================================================== shared helpers

    /** Draws a list of items (icon + durability or count), one per row. */
    static int[] itemList(Renderer r, List<ItemInfo> items) {
        int y = 0;
        int width = 16;
        for (ItemInfo item : items) {
            r.item(item.handle, 0, y);
            String text = item.maxDamage > 0 ? String.valueOf(item.durabilityLeft())
                    : (item.count > 1 ? String.valueOf(item.count) : "");
            if (!text.isEmpty()) {
                int colour = item.maxDamage > 0 && item.durabilityLeft() < item.maxDamage / 4 ? 0xFFFF6B5E : 0xFFFFFFFF;
                r.text(text, 20, y + 4, colour, true);
                width = Math.max(width, 20 + r.textWidth(text));
            }
            y += 18;
        }
        return new int[] {width, Math.max(y, 18)};
    }

    /** A labelled empty box for the HUD editor when there's nothing to show yet. */
    static int[] exampleBox(Renderer r, String label, int rows) {
        int width = Math.max(40, r.textWidth(label) + 8);
        int height = rows * 18;
        r.rect(0, 0, width, height, 0x60101018);
        r.text(label, 4, 4, 0xFFAAAAAA, false);
        return new int[] {width, height};
    }

    /** 45° steps of the yaw as a direction name. */
    static String facing(float yaw) {
        String[] names = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
        int index = Math.round((((yaw % 360) + 360) % 360) / 45f) % 8;
        return names[index];
    }

    static String roman(int number) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number < numerals.length ? numerals[number] : String.valueOf(number);
    }
}
