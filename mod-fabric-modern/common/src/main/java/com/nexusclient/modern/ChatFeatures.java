package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.HudMods;
import com.nexusclient.core.hud.SessionStats;
import com.nexusclient.core.module.Mods;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ChatFeatures – the chat mods (the project brief: timestamps, stack duplicate messages, mention
 * highlighting, chat filter) plus Nick Hider and Auto GG. Every chat message passes through here
 * (see mixin/ChatComponentMixin) before it's shown.
 */
public final class ChatFeatures {

    private static String lastText = "";
    private static int repeats = 1;
    private static long lastAutoGg;

    private ChatFeatures() {}

    /** Returns null to hide the message, or the (possibly changed) message to show. */
    public static Component process(Component message) {
        NexusCore core = NexusCore.get();
        String plain = message.getString();

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
                && System.currentTimeMillis() - lastAutoGg > 10_000) {
            lastAutoGg = System.currentTimeMillis();
            core.platform().sendChat(autoGg.message.get());
        }

        MutableComponent result = message.copy();

        // Nick hider: show your own name as something else (only on your screen).
        Mods.NickHider nickHider = core.get(Mods.NickHider.class);
        String ownName = core.game().playerName();
        if (nickHider.isActive() && plain.contains(ownName)) {
            result = Component.literal(plain.replace(ownName, nickHider.fakeName.get())).withStyle(message.getStyle());
        }

        // Mention highlight: colour the message if it mentions you (or your extra words).
        Mods.MentionHighlight mention = core.get(Mods.MentionHighlight.class);
        if (mention.isActive() && (plain.toLowerCase(Locale.ROOT).contains(ownName.toLowerCase(Locale.ROOT))
                || containsAny(plain, mention.extraWords.get()))) {
            result = Component.literal(result.getString())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(mention.color.argb() & 0xFFFFFF)));
        }

        // Stack duplicates: the same message again becomes "message (x2)".
        Mods.ChatStacking stacking = core.get(Mods.ChatStacking.class);
        if (stacking.isActive()) {
            if (plain.equals(lastText)) {
                repeats++;
                result = result.append(Component.literal(" (x" + repeats + ")")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7C7C96))));
            } else {
                repeats = 1;
            }
        }
        lastText = plain;

        // Timestamps: "[12:34] message"
        Mods.ChatTimestamps timestamps = core.get(Mods.ChatTimestamps.class);
        if (timestamps.isActive()) {
            String time = "[" + new SimpleDateFormat(timestamps.format.get()).format(new Date()) + "] ";
            result = Component.literal(time)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(timestamps.color.argb() & 0xFFFFFF)))
                    .append(result);
        }
        return result;
    }

    /** True if the previous message was the same text (so the stacked copy should replace it). */
    public static boolean isRepeat() {
        return repeats > 1;
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
