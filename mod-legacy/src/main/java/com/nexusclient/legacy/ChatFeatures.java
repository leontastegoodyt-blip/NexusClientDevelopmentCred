package com.nexusclient.legacy;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.HudMods;
import com.nexusclient.core.hud.SessionStats;
import com.nexusclient.core.module.Mods;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ChatFeatures – the chat mods for 1.8.9 (timestamps, stack duplicate messages, mention
 * highlighting, chat filter) plus Nick Hider and Auto GG. Every chat message passes through here
 * (see ChatHudMixin) before it's shown.
 *
 * Differences from the newer versions:
 *  - 1.8.9 chat only has 16 colours, so the colours picked in the settings are matched to the
 *    nearest one.
 *  - Stacking uses 1.8.9's "message id": a new message with the same id replaces the old one.
 */
public final class ChatFeatures {

    /** The changed message, and the id to add it with (0 = a normal message). */
    public static final class Result {
        public final Text text;
        public final int id;

        Result(Text text, int id) {
            this.text = text;
            this.id = id;
        }
    }

    /** Message ids Nexus uses for stacking (chosen high so they never clash with Minecraft's own). */
    private static final int FIRST_STACK_ID = 0x4E580000;

    private static String lastText = "";
    private static int repeats = 1;
    private static int nextStackId = FIRST_STACK_ID;
    private static int lastId;
    private static long lastAutoGg;

    private ChatFeatures() {}

    /** Returns null to hide the message, or the (possibly changed) message to show. */
    public static Result process(Text message, int messageId) {
        NexusCore core = NexusCore.get();
        String plain = message.asUnformattedString();

        // PvP stats: spot "you won" messages.
        HudMods.PvpStats pvpStats = core.get(HudMods.PvpStats.class);
        if (pvpStats.isActive()) {
            SessionStats.chatMessage(plain, pvpStats.winMessages.get());
        }

        // Chat filter: hide messages with blocked words.
        Mods.ChatFilter filter = core.get(Mods.ChatFilter.class);
        if (filter.isActive() && containsAny(plain, filter.words.get())) {
            return null;
        }

        // Auto GG: say gg when a game ends (at most once every 10 seconds).
        Mods.AutoGG autoGg = core.get(Mods.AutoGG.class);
        if (autoGg.isActive() && containsAny(plain, autoGg.triggers.get())
                && System.currentTimeMillis() - lastAutoGg > 10000) {
            lastAutoGg = System.currentTimeMillis();
            core.platform().sendChat(autoGg.message.get());
        }

        Text result = message.copy();

        // Nick hider: show your own name as something else (only on your screen).
        // Uses the text with its colour codes, so the message keeps its colours.
        Mods.NickHider nickHider = core.get(Mods.NickHider.class);
        String ownName = core.game().playerName();
        if (nickHider.isActive() && plain.contains(ownName)) {
            result = new LiteralText(result.asFormattedString().replace(ownName, nickHider.fakeName.get()));
        }

        // Mention highlight: colour the message if it mentions you (or your extra words).
        Mods.MentionHighlight mention = core.get(Mods.MentionHighlight.class);
        if (mention.isActive() && (plain.toLowerCase(Locale.ROOT).contains(ownName.toLowerCase(Locale.ROOT))
                || containsAny(plain, mention.extraWords.get()))) {
            result = new LiteralText(result.asUnformattedString()).setStyle(colour(mention.color.argb()));
        }

        // Stack duplicates: the same message again replaces the old line with "message (x2)".
        int id = messageId;
        Mods.ChatStacking stacking = core.get(Mods.ChatStacking.class);
        if (stacking.isActive()) {
            if (plain.equals(lastText)) {
                repeats++;
                result.append(new LiteralText(" (x" + repeats + ")").setStyle(new Style().setFormatting(Formatting.GRAY)));
                id = lastId; // same id = Minecraft removes the previous copy
            } else {
                repeats = 1;
                if (id == 0) {
                    id = nextStackId++;
                    if (nextStackId == FIRST_STACK_ID + 100000) {
                        nextStackId = FIRST_STACK_ID; // start again (old ids are long gone by then)
                    }
                }
            }
            lastId = id;
        } else {
            repeats = 1;
        }
        lastText = plain;

        // Timestamps: "[12:34] message"
        Mods.ChatTimestamps timestamps = core.get(Mods.ChatTimestamps.class);
        if (timestamps.isActive()) {
            String time = "[" + new SimpleDateFormat(timestamps.format.get()).format(new Date()) + "] ";
            result = new LiteralText(time).setStyle(colour(timestamps.color.argb())).append(result);
        }
        return new Result(result, id);
    }

    /** The 16 chat colours of 1.8.9, in order (black … white). */
    private static final int[] COLOURS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final Formatting[] FORMATS = {Formatting.BLACK, Formatting.DARK_BLUE, Formatting.DARK_GREEN,
            Formatting.DARK_AQUA, Formatting.DARK_RED, Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY,
            Formatting.DARK_GRAY, Formatting.BLUE, Formatting.GREEN, Formatting.AQUA, Formatting.RED,
            Formatting.LIGHT_PURPLE, Formatting.YELLOW, Formatting.WHITE};

    /** A style with the chat colour closest to the given colour. */
    private static Style colour(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < COLOURS.length; i++) {
            int dr = r - ((COLOURS[i] >> 16) & 0xFF);
            int dg = g - ((COLOURS[i] >> 8) & 0xFF);
            int db = b - (COLOURS[i] & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return new Style().setFormatting(FORMATS[best]);
    }

    /** True if the text contains any of the comma-separated words (ignoring capitals). */
    private static boolean containsAny(String text, String commaSeparated) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : commaSeparated.split(",")) {
            String trimmed = word.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && lower.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
