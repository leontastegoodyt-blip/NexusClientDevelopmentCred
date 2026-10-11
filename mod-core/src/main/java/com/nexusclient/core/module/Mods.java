package com.nexusclient.core.module;

import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;
import com.nexusclient.core.settings.ColorSetting;
import com.nexusclient.core.settings.NumberSetting;
import com.nexusclient.core.settings.TextSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mods – the PvP, visual, chat and utility mods from the project brief (section 4).
 *
 * These classes hold each mod's settings and on/off state. The work itself (changing the camera,
 * editing chat messages…) happens in the loader module, because it needs to reach inside Minecraft.
 * Server-blockable mods are marked (the project brief: server-safe rules).
 *
 * NEVER add anything that gives an unfair advantage (reach, autoclicker, x-ray, kill aura…).
 */
public final class Mods {

    private Mods() {}

    /** All non-HUD mods, in the order they appear in the mod menu. */
    public static List<Module> all() {
        return new ArrayList<Module>(Arrays.<Module>asList(
                // PvP
                new ToggleSprint(), new ToggleSneak(), new Crosshair(), new HitColor(), new BlockOutline(),
                new OldAnimations(), new Hitboxes(), new SmallItems(), new ItemPhysics(), new LowFire(),
                new NoHurtCam(), new Particles(), new EnchantGlint(),
                // Visual
                new Zoom(), new MotionBlur(), new Freelook(), new Fullbright(), new TimeChanger(),
                new WeatherChanger(), new Nametags(), new com.nexusclient.core.map.Waypoints(), new com.nexusclient.core.dfr.Dfr(),
                // Chat
                new ChatTimestamps(), new CopyChat(), new ChatStacking(), new LongerChat(),
                new MentionHighlight(), new ChatFilter(),
                // Utility
                new ScreenshotUploader(), new NickHider(), new AutoGG(), new StreamerMode(), new ClipRecorder()));
    }

    // ================================================================== PvP

    public static class ToggleSprint extends Module {
        /** Whether sprinting is currently toggled on (flipped by pressing the sprint key). */
        public boolean toggled = true;

        public ToggleSprint() {
            super("toggleSprint", "Toggle Sprint", "Press sprint once to keep sprinting – no holding.", Category.PVP, false, false);
        }
    }

    public static class ToggleSneak extends Module {
        public boolean toggled = false;

        public ToggleSneak() {
            super("toggleSneak", "Toggle Sneak", "Press sneak once to keep sneaking.", Category.PVP, false, false);
        }
    }

    /** A custom crosshair, drawn by mod-core (so it looks the same in every version). */
    public static class Crosshair extends Module {
        public final ChoiceSetting style = add(new ChoiceSetting("style", "Style", "Cross", "Cross", "Plus + dot", "Dot", "Circle", "T"));
        public final NumberSetting size = add(new NumberSetting("size", "Size", 5, 1, 15, 1, ""));
        public final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 10, 1, ""));
        public final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1, 1, 5, 1, ""));
        public final ColorSetting color = add(new ColorSetting("color", "Colour", 0xFF3AA8FF));
        public final BooleanSetting outline = add(new BooleanSetting("outline", "Black outline", true));

        public Crosshair() {
            super("crosshair", "Custom Crosshair", "Design your own crosshair (with presets and share codes).", Category.PVP, false, false);
        }

        /** Draws the crosshair centred on (cx, cy). */
        public void draw(Renderer r, int cx, int cy) {
            int s = size.intValue();
            int g = gap.intValue();
            int t = thickness.intValue();
            int c = color.argb();
            if (style.is("Dot") || style.is("Plus + dot")) {
                box(r, cx - t, cy - t, cx + t, cy + t, c);
            }
            if (style.is("Cross") || style.is("Plus + dot") || style.is("T")) {
                int half = t / 2;
                if (!style.is("T")) {
                    box(r, cx - half, cy - g - s, cx - half + t, cy - g, c); // up
                }
                box(r, cx - half, cy + g, cx - half + t, cy + g + s, c);     // down
                box(r, cx - g - s, cy - half, cx - g, cy - half + t, c);     // left
                box(r, cx + g, cy - half, cx + g + s, cy - half + t, c);     // right
            }
            if (style.is("Circle")) {
                int radius = s + g;
                for (int angle = 0; angle < 360; angle += 10) {
                    int x = cx + (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
                    int y = cy + (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);
                    box(r, x, y, x + t, y + t, c);
                }
            }
        }

        private void box(Renderer r, int x1, int y1, int x2, int y2, int colour) {
            if (outline.isOn()) {
                r.rect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xC0000000);
            }
            r.rect(x1, y1, x2, y2, colour);
        }
    }

    public static class HitColor extends Module {
        public final ColorSetting color = add(new ColorSetting("color", "Hit colour", 0x80FF2BD6));

        public HitColor() {
            super("hitColor", "Hit Colour", "Change the colour mobs and players flash when hit.", Category.PVP, false, false);
        }
    }

    public static class BlockOutline extends Module {
        public final ColorSetting color = add(new ColorSetting("color", "Outline colour", 0xFF3AA8FF));
        public final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 2, 1, 6, 0.5, ""));

        public BlockOutline() {
            super("blockOutline", "Block Outline", "Change the colour and thickness of the block outline.", Category.PVP, false, false);
        }
    }

    public static class OldAnimations extends Module {
        public OldAnimations() {
            super("oldAnimations", "1.7 Animations", "Old-style sword blocking and swing animations (1.8.9).", Category.PVP, false, false);
        }
    }

    public static class Hitboxes extends Module {
        public final ColorSetting color = add(new ColorSetting("color", "Hitbox colour", 0xFFFFFFFF));
        public final BooleanSetting showOnPlayersOnly = add(new BooleanSetting("playersOnly", "Players only", false));

        public Hitboxes() {
            super("hitboxes", "Hitboxes", "Customise how hitboxes look (F3+B).", Category.PVP, false, false);
        }
    }

    public static class SmallItems extends Module {
        public final NumberSetting scale = add(new NumberSetting("scale", "Held item size", 0.7, 0.3, 1.0, 0.05, "x"));

        public SmallItems() {
            super("smallItems", "Small Items", "Makes the item in your hand smaller so you can see more.", Category.PVP, false, false);
        }
    }

    public static class ItemPhysics extends Module {
        public ItemPhysics() {
            super("itemPhysics", "Item Physics", "Dropped items lie flat on the ground instead of spinning.", Category.PVP, false, false);
        }
    }

    public static class LowFire extends Module {
        public final NumberSetting height = add(new NumberSetting("height", "Fire height", 0.6, 0.1, 1.0, 0.05, "x"));

        public LowFire() {
            super("lowFire", "Low Fire", "Makes the fire on your screen smaller when you burn.", Category.PVP, false, false);
        }
    }

    public static class NoHurtCam extends Module {
        public NoHurtCam() {
            super("noHurtCam", "No Hurt-Cam Shake", "Stops the screen shaking when you take damage.", Category.PVP, false, false);
        }
    }

    public static class Particles extends Module {
        public final NumberSetting multiplier = add(new NumberSetting("multiplier", "Hit particles", 1, 0, 5, 1, "x"));

        public Particles() {
            super("particles", "Particle Multiplier", "Show more (or fewer) hit particles.", Category.PVP, false, false);
        }
    }

    public static class EnchantGlint extends Module {
        public final ColorSetting color = add(new ColorSetting("color", "Glint colour", 0xFFFF2BD6));

        public EnchantGlint() {
            super("enchantGlint", "Enchant Glint Colour", "Change the colour of the enchantment shine.", Category.PVP, false, false);
        }
    }

    // ================================================================== Visual

    public static class Zoom extends Module {
        public final NumberSetting factor = add(new NumberSetting("factor", "Zoom", 4, 2, 15, 0.5, "x"));
        public final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth zoom", true));
        public final BooleanSetting scroll = add(new BooleanSetting("scroll", "Scroll to adjust", true));
        /** How far zoomed in right now (1 = not zoomed). Changed by the loader module each frame. */
        public double current = 1;
        /** Zoom level while the key is held (scrolling changes it). */
        public double target = 4;

        public Zoom() {
            super("zoom", "Zoom", "Hold C to zoom in (scroll to zoom more or less).", Category.VISUAL, true, false);
        }
    }

    public static class MotionBlur extends Module {
        public final NumberSetting strength = add(new NumberSetting("strength", "Strength", 50, 10, 90, 5, "%"));

        public MotionBlur() {
            super("motionBlur", "Motion Blur", "Adds a smooth blur when you move the camera.", Category.VISUAL, false, false);
        }
    }

    /**
     * Freelook: while the key is held, the camera swings around the player (in third person) and
     * the mouse only turns the camera – the player keeps facing and walking the same way.
     * The loader modules do the game side: switch the view, send mouse movement to turn(), and show
     * the camera from yaw/pitch here instead of the player's.
     */
    public static class Freelook extends Module {
        /** True while the key is held and the camera is free. */
        public boolean looking;
        /** Where the free camera looks (degrees, like the player's own yaw and pitch). */
        public float yaw;
        public float pitch;
        /** The view (first/third person) to go back to afterwards – stored by the loader module. */
        public Object savedView;

        public Freelook() {
            super("freelook", "Freelook", "Hold Left Alt to look around without turning your player.", Category.VISUAL, false, true);
        }

        /** The key was pressed: start looking from where the player is facing. */
        public void start(float playerYaw, float playerPitch) {
            looking = true;
            yaw = playerYaw;
            pitch = playerPitch;
        }

        /** Mouse movement while looking (same speed as turning normally). Down is positive pitch. */
        public void turn(double dx, double dy) {
            yaw += (float) (dx * 0.15);
            pitch = Math.max(-90, Math.min(90, pitch + (float) (dy * 0.15)));
        }

        /** The key was let go (or a server switched the mod off). */
        public void stop() {
            looking = false;
        }
    }

    public static class Fullbright extends Module {
        public Fullbright() {
            super("fullbright", "Fullbright", "See clearly in the dark.", Category.VISUAL, false, true);
        }
    }

    public static class TimeChanger extends Module {
        public final NumberSetting time = add(new NumberSetting("time", "Time of day", 6000, 0, 24000, 500, ""));

        public TimeChanger() {
            super("timeChanger", "Time Changer", "Change the time of day – only on your screen.", Category.VISUAL, false, false);
        }

        /**
         * The time the player should see. Keeps the real day number (so the moon phase stays right)
         * but swaps in the chosen time of day. Returns the real time when the mod is off.
         *
         * @param realTime Minecraft's time in ticks (24000 per day)
         */
        public long apply(long realTime) {
            if (!isActive()) {
                return realTime;
            }
            long day = Math.floorDiv(realTime, 24000L);
            return day * 24000L + time.intValue();
        }
    }

    public static class WeatherChanger extends Module {
        public final ChoiceSetting weather = add(new ChoiceSetting("weather", "Weather", "Clear", "Clear", "Rain", "Thunder"));

        public WeatherChanger() {
            super("weatherChanger", "Weather Changer", "Change the weather – only on your screen.", Category.VISUAL, false, false);
        }
    }

    public static class Nametags extends Module {
        public final BooleanSetting ownNametag = add(new BooleanSetting("own", "Show my own nametag (F5)", true));

        public Nametags() {
            super("nametags", "Custom Nametags", "Show your own nametag in third person (F5).", Category.VISUAL, false, false);
        }
    }


    // ================================================================== Chat

    public static class ChatTimestamps extends Module {
        public final ChoiceSetting format = add(new ChoiceSetting("format", "Format", "HH:mm", "HH:mm", "HH:mm:ss", "h:mm a"));
        public final ColorSetting color = add(new ColorSetting("color", "Colour", 0xFF7C7C96));

        public ChatTimestamps() {
            super("chatTimestamps", "Chat Timestamps", "Shows the time next to each chat message.", Category.CHAT, false, false);
        }
    }

    public static class CopyChat extends Module {
        public CopyChat() {
            super("copyChat", "Copy Chat", "Right-click a chat message to copy it.", Category.CHAT, false, false);
        }
    }

    public static class ChatStacking extends Module {
        public ChatStacking() {
            super("chatStacking", "Stack Duplicates", "Repeated messages show once with a (x3) counter.", Category.CHAT, false, false);
        }
    }

    public static class LongerChat extends Module {
        public final NumberSetting lines = add(new NumberSetting("lines", "Chat history", 1000, 100, 5000, 100, " lines"));

        public LongerChat() {
            super("longerChat", "Longer Chat History", "Keep more chat messages to scroll back through.", Category.CHAT, false, false);
        }
    }

    public static class MentionHighlight extends Module {
        public final ColorSetting color = add(new ColorSetting("color", "Highlight colour", 0xFFFFB547));
        public final TextSetting extraWords = add(new TextSetting("words", "Also highlight (comma separated)", "", 200));

        public MentionHighlight() {
            super("mentionHighlight", "Mention Highlight", "Highlights messages that mention your name.", Category.CHAT, false, false);
        }
    }

    public static class ChatFilter extends Module {
        public final TextSetting words = add(new TextSetting("words", "Hide messages containing (comma separated)", "", 400));

        public ChatFilter() {
            super("chatFilter", "Chat Filter", "Hides messages with words you don't want to see.", Category.CHAT, false, false);
        }
    }

    // ================================================================== Utility

    /** Save the last 15–60 seconds as a video with one key (see record/ClipRecorder for how). */
    public static class ClipRecorder extends Module {
        public final NumberSetting length = add(new NumberSetting("length", "Clip length", 30, 15, 60, 5, " s"));
        public final ChoiceSetting fps = add(new ChoiceSetting("fps", "Frame rate", "60", "30", "60"));
        public final ChoiceSetting quality = add(new ChoiceSetting("quality", "Quality", "Medium", "Low", "Medium", "High"));
        public final ChoiceSetting area = add(new ChoiceSetting("area", "Record", "Game window", "Game window", "Whole screen"));

        public ClipRecorder() {
            super("clipRecorder", "Clip Recorder",
                    "Press F8 to save the last 30 seconds as a video (in the clips folder).", Category.UTILITY, false, false);
        }
    }

    public static class ScreenshotUploader extends Module {
        public ScreenshotUploader() {
            super("screenshotUploader", "Screenshot Uploader", "Upload a screenshot and copy its link (needs the Nexus servers, build step 13).", Category.UTILITY, false, false);
        }
    }

    public static class NickHider extends Module {
        public final TextSetting fakeName = add(new TextSetting("name", "Show my name as", "You", 16));

        public NickHider() {
            super("nickHider", "Nick Hider", "Hides your real name on your own screen.", Category.UTILITY, false, false);
        }
    }

    public static class AutoGG extends Module {
        public final TextSetting message = add(new TextSetting("message", "Message", "gg", 64));
        public final TextSetting triggers = add(new TextSetting("triggers", "When chat says (comma separated)",
                "Winner:,1st Killer,You won,Victory!,won the game", 300));

        public AutoGG() {
            super("autoGG", "Auto GG", "Says \"gg\" automatically at the end of a game.", Category.UTILITY, false, true);
        }
    }

    public static class StreamerMode extends Module {
        public final BooleanSetting hideIps = add(new BooleanSetting("hideIps", "Hide server addresses", true));
        public final BooleanSetting hideCoords = add(new BooleanSetting("hideCoords", "Hide coordinates", true));
        public final BooleanSetting hideFriends = add(new BooleanSetting("hideFriends", "Hide friends' names", true));

        public StreamerMode() {
            super("streamerMode", "Streamer Mode", "Hides server addresses, coordinates and friends' names.", Category.UTILITY, false, false);
        }

        /** True if the Coordinates HUD (and other places that show where you are) should hide them. */
        public boolean hidesCoordinates() {
            return isActive() && hideCoords.isOn();
        }

        /** True if server addresses should be hidden (Server Address HUD, Discord status…). */
        public boolean hidesServerAddresses() {
            return isActive() && hideIps.isOn();
        }

        /** True if friends' names should be hidden (friend pop-ups once they exist in-game). */
        public boolean hidesFriends() {
            return isActive() && hideFriends.isOn();
        }
    }
}
