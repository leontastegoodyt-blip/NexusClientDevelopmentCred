package com.nexusclient.fabric;

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
            codes.append('§').append(nearest(colour.getValue()));
        }
        if (style.isBold()) codes.append("§l");
        if (style.isItalic()) codes.append("§o");
        if (style.isUnderlined()) codes.append("§n");
        if (style.isStrikethrough()) codes.append("§m");
        if (style.isObfuscated()) codes.append("§k");
        return codes.toString();
    }

    /** The 16 classic chat colours: their code letters and colours. */
    private static final String CODES = "0123456789abcdef";
    private static final int[] COLOURS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** The code letter of the classic chat colour closest to an RGB colour. */
    private static char nearest(int rgb) {
        char best = 'f';
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < COLOURS.length; i++) {
            int c = COLOURS[i];
            long dr = ((rgb >> 16) & 255) - ((c >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((c >> 8) & 255);
            long db = (rgb & 255) - (c & 255);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = CODES.charAt(i);
            }
        }
        return best;
    }
}
