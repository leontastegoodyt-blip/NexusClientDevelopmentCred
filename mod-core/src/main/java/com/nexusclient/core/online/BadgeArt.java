package com.nexusclient.core.online;

/**
 * BadgeArt – the badge pictures as pixel art (ARGB colours, row by row).
 *
 * Minecraft 1.20+ shows badges as letters of the Nexus badge font (assets/nexusclient/font, made from
 * these same pictures). Minecraft 1.8.9 can't load fonts from mods, so there the name tag draws these
 * pixels itself as tiny squares (see mod-legacy's BadgeMixin). Keeping the art here means both look
 * exactly the same. 0 = see-through.
 */
public final class BadgeArt {

    // B = edge, L = letter N, D = dark inside.
    private static final String[] HEX = {
            ".......BB.......",
            ".....BBDDBB.....",
            "...BBDDDDDDBB...",
            ".BBDDDDDDDDDDBB.",
            ".BDDLLDDDDLLDDB.",
            ".BDDLLLDDDLLDDB.",
            ".BDDLLLLDDLLDDB.",
            ".BDDLLDLLDLLDDB.",
            ".BDDLLDDLLLLDDB.",
            ".BDDLLDDDLLLDDB.",
            ".BDDLLDDDDLLDDB.",
            ".BDDDDDDDDDDDDB.",
            ".BBDDDDDDDDDDBB.",
            "...BBDDDDDDBB...",
            ".....BBDDBB.....",
            ".......BB.......",
    };

    // The Premium "+" over the bottom-right corner (P = plus, O = dark outline).
    private static final String[] PLUS = {
            "", "", "", "", "", "", "", "", "",
            "...........OOO..",
            "...........OPO..",
            ".........OOOPOOO",
            ".........OPPPPPO",
            ".........OOOPOOO",
            "...........OPO..",
            "...........OOO..",
    };

    private static final String[][] DEV_LETTERS = {
            {"XX.", "X.X", "X.X", "X.X", "XX."},   // D
            {"XXX", "X..", "XX.", "X..", "XXX"},   // E
            {"X.X", "X.X", "X.X", "X.X", ".X."},   // V
    };

    private static int[][] nexus;
    private static int[][] premium;
    private static int[][] devTag;

    private BadgeArt() {}

    /** The blue N hexagon, 16 x 16. */
    public static synchronized int[][] nexus() {
        if (nexus == null) {
            nexus = hexagon(0x5FD3FF, 0x2F6BFF, 0xD6F4FF, 0x5FD3FF, 0x0A1430, false);
        }
        return nexus;
    }

    /** The gold N hexagon with a "+", 16 x 16. */
    public static synchronized int[][] premium() {
        if (premium == null) {
            premium = hexagon(0xFFD46B, 0xC9820E, 0xFFF4C8, 0xFFC23A, 0x241904, true);
        }
        return premium;
    }

    /** The "DEV" pill: a blue → violet gradient with dark letters, 32 x 16. */
    public static synchronized int[][] devTag() {
        if (devTag == null) {
            int[][] art = new int[16][32];
            for (int y = 1; y < 15; y++) {
                for (int x = 0; x < 32; x++) {
                    boolean corner = (y == 1 || y == 14) && (x < 2 || x > 29) || (y == 2 || y == 13) && (x == 0 || x == 31);
                    if (!corner) {
                        double t = x / 31.0;
                        int rgb = t < 0.45 ? mix(0x5FD3FF, 0x3AA8FF, t / 0.45) : mix(0x3AA8FF, 0x8B5CFF, (t - 0.45) / 0.55);
                        art[y][x] = 0xFF000000 | rgb;
                    }
                }
            }
            int left = 5;
            for (String[] letter : DEV_LETTERS) {
                for (int ly = 0; ly < 5; ly++) {
                    for (int lx = 0; lx < 3; lx++) {
                        if (letter[ly].charAt(lx) == 'X') {
                            for (int d = 0; d < 4; d++) {
                                art[3 + ly * 2 + d / 2][left + lx * 2 + d % 2] = 0xFF07060E;
                            }
                        }
                    }
                }
                left += 8;
            }
            devTag = art;
        }
        return devTag;
    }

    private static int[][] hexagon(int edgeTop, int edgeBottom, int letterTop, int letterBottom, int dark, boolean plus) {
        int[][] art = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                char c = HEX[y].charAt(x);
                int rgb;
                if (c == 'B') {
                    rgb = mix(edgeTop, edgeBottom, y / 15.0);
                } else if (c == 'L') {
                    rgb = mix(letterTop, letterBottom, (x + y) / 30.0);
                } else if (c == 'D') {
                    rgb = dark;
                } else {
                    continue;
                }
                art[y][x] = 0xFF000000 | rgb;
            }
            if (plus && PLUS[y].length() == 16) {
                for (int x = 0; x < 16; x++) {
                    char c = PLUS[y].charAt(x);
                    if (c == 'P') {
                        art[y][x] = 0xFFFFFFFF;
                    } else if (c == 'O') {
                        art[y][x] = 0xFF000000 | dark;
                    }
                }
            }
        }
        return art;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
