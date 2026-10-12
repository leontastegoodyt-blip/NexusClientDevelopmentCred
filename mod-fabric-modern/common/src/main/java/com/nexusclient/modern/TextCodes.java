package com.nexusclient.modern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * TextCodes – turns Minecraft's coloured text (a Component) into a plain string with "§" colour codes,
 * which the Nexus HUD can draw (Minecraft's font understands those codes). Custom colours (any RGB)
 * become the nearest of the 16 classic chat colours. Used by Custom Scoreboard and Custom Boss Bar.
 */
public final class TextCodes {

    private TextCodes() {}

    public static String legacy(Component component) {
        if (component == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                out.append(codes(style)).append(text);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    private static String codes(Style style) {
        StringBuilder codes = new StringBuilder("§r");
        TextColor colour = style.getColor();
        if (colour != null) {
            codes.append('§').append(nearest(colour.getValue()).getChar());
        }
        if (style.isBold()) codes.append("§l");
        if (style.isItalic()) codes.append("§o");
        if (style.isUnderlined()) codes.append("§n");
        if (style.isStrikethrough()) codes.append("§m");
        if (style.isObfuscated()) codes.append("§k");
        return codes.toString();
    }

    private static ChatFormatting nearest(int rgb) {
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (ChatFormatting format : ChatFormatting.values()) {
            Integer c = format.getColor();
            if (c == null) {
                continue;
            }
            long dr = ((rgb >> 16) & 255) - ((c >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((c >> 8) & 255);
            long db = (rgb & 255) - (c & 255);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = format;
            }
        }
        return best;
    }
}
