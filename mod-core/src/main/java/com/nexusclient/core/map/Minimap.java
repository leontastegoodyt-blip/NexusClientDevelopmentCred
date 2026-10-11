package com.nexusclient.core.map;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;
import com.nexusclient.core.settings.NumberSetting;
import com.nexusclient.core.ui.UiKit;

/**
 * Minimap – a small map of the area around you, north at the top, like a paper map in the corner.
 *
 * How it works: the map is a grid of little squares ("cells"). Each cell is coloured like the top
 * block at that spot (grass green, water blue, sand yellow… – Minecraft's own map colours), a bit
 * lighter where the ground goes up and darker where it goes down, so hills show. Asking the world
 * for thousands of blocks every frame would be slow, so the colours are worked out again only
 * twice a second (or when you've moved), and kept in between.
 *
 * Also drawn: your position and facing (white arrow), waypoints (coloured dots, on the edge when far
 * away), and "N" for north. Server-blockable (some servers ban minimaps).
 */
public class Minimap extends HudModule {

    public final NumberSetting size = add(new NumberSetting("size", "Size", 96, 64, 192, 8, ""));
    public final ChoiceSetting zoom = add(new ChoiceSetting("zoom", "Zoom", "1 block", "1 block", "2 blocks", "4 blocks"));
    public final BooleanSetting showCoords = add(new BooleanSetting("coords", "Show coordinates", true));
    public final BooleanSetting showWaypoints = add(new BooleanSetting("waypoints", "Show waypoints", true));

    private static final int CELL = 2; // each cell is 2 x 2 pixels on the map

    private int[] colours = new int[0];
    private int cellsAcross;
    private int centreX = Integer.MIN_VALUE;
    private int centreZ = Integer.MIN_VALUE;
    private int blocksPerCell = 1;
    private long lastRefresh;

    public Minimap() {
        super("minimap", "Minimap", "A small map of the area around you.", Category.VISUAL, false, true, 0.82, 0.02);
    }

    @Override
    protected int[] draw(Renderer r, GameInfo game, boolean preview) {
        int mapSize = size.get().intValue();
        int cells = mapSize / CELL;
        mapSize = cells * CELL;
        int footer = showCoords.isOn() ? 12 : 0;

        // The frame: rounded, with the Nexus gradient line along the top.
        UiKit.roundBox(r, 0, 0, mapSize + 4, mapSize + 4 + footer, 4, 0xE00A0A12, 0xFF26263E);
        UiKit.gradientLine(r, 4, 0, mapSize, 0xD0);

        if (preview || !game.inWorld()) {
            drawPreview(r, cells);
        } else {
            refreshIfNeeded(game, cells);
            drawCells(r, cells);
            drawWaypoints(r, game, cells, mapSize);
            drawPlayer(r, game, mapSize);
        }
        // North marker.
        UiKit.round(r, mapSize / 2 - 2, 2, mapSize / 2 + 7, 11, 2, 0xC0101018);
        r.text("N", mapSize / 2, 3, 0xFFFF6B5E, false);
        if (footer > 0) {
            String coords = preview ? "128, 64, -303"
                    : (int) Math.floor(game.x()) + ", " + (int) Math.floor(game.y()) + ", " + (int) Math.floor(game.z());
            UiKit.centered(r, coords, (mapSize + 4) / 2, mapSize + 6, UiKit.TEXT, false);
        }
        return new int[] {mapSize + 4, mapSize + 4 + footer};
    }

    /** Works the colours out again when you've moved, zoomed, or every half second. */
    private void refreshIfNeeded(GameInfo game, int cells) {
        int bpc = zoom.is("4 blocks") ? 4 : zoom.is("2 blocks") ? 2 : 1;
        int px = (int) Math.floor(game.x());
        int pz = (int) Math.floor(game.z());
        long now = System.currentTimeMillis();
        boolean moved = Math.abs(px - centreX) >= bpc || Math.abs(pz - centreZ) >= bpc;
        if (cells == cellsAcross && bpc == blocksPerCell && !moved && now - lastRefresh < 500) {
            return;
        }
        cellsAcross = cells;
        blocksPerCell = bpc;
        centreX = px;
        centreZ = pz;
        lastRefresh = now;
        if (colours.length != cells * cells) {
            colours = new int[cells * cells];
        }
        int half = cells / 2;
        // Heights of the row above (north), to shade slopes like Minecraft's paper maps.
        int[] northHeights = new int[cells];
        for (int cx = 0; cx < cells; cx++) {
            northHeights[cx] = game.surfaceY(px + (cx - half) * bpc, pz + (-half - 1) * bpc);
        }
        for (int cz = 0; cz < cells; cz++) {
            for (int cx = 0; cx < cells; cx++) {
                int wx = px + (cx - half) * bpc;
                int wz = pz + (cz - half) * bpc;
                int rgb = game.mapColour(wx, wz);
                int height = game.surfaceY(wx, wz);
                if (rgb != 0) {
                    // Higher than the block to the north = lighter, lower = darker.
                    double shade = height > northHeights[cx] ? 1.12 : height < northHeights[cx] ? 0.80 : 0.96;
                    rgb = shade(rgb, shade);
                }
                northHeights[cx] = height;
                colours[cz * cells + cx] = rgb;
            }
        }
    }

    /** Draws the cells, joining neighbours of the same colour into one rectangle (much faster). */
    private void drawCells(Renderer r, int cells) {
        for (int cz = 0; cz < cells; cz++) {
            int start = 0;
            for (int cx = 1; cx <= cells; cx++) {
                if (cx == cells || colours[cz * cells + cx] != colours[cz * cells + start]) {
                    int rgb = colours[cz * cells + start];
                    r.rect(2 + start * CELL, 2 + cz * CELL, 2 + cx * CELL, 2 + (cz + 1) * CELL,
                            rgb == 0 ? 0xFF0C0C14 : 0xFF000000 | rgb);
                    start = cx;
                }
            }
        }
    }

    private void drawWaypoints(Renderer r, GameInfo game, int cells, int mapSize) {
        Waypoints waypoints = NexusCore.get().get(Waypoints.class);
        if (!showWaypoints.isOn() || waypoints == null || !waypoints.isActive()) {
            return;
        }
        double pixelsPerBlock = CELL / (double) blocksPerCell;
        for (WaypointStore.Waypoint waypoint : waypoints.current(game)) {
            if (!waypoint.visible) {
                continue;
            }
            double mx = mapSize / 2.0 + (waypoint.x + 0.5 - game.x()) * pixelsPerBlock;
            double my = mapSize / 2.0 + (waypoint.z + 0.5 - game.z()) * pixelsPerBlock;
            // Far away: keep it on the map's edge, pointing the right way.
            mx = Math.max(3, Math.min(mapSize - 2, mx));
            my = Math.max(3, Math.min(mapSize - 2, my));
            int x = 2 + (int) mx;
            int y = 2 + (int) my;
            r.rect(x - 2, y - 2, x + 3, y + 3, 0xFF07060E);
            r.rect(x - 1, y - 1, x + 2, y + 2, 0xFF000000 | waypoint.colour);
        }
    }

    /** Your position in the middle, with an arrow showing which way you face. */
    private void drawPlayer(Renderer r, GameInfo game, int mapSize) {
        int cx = 2 + mapSize / 2;
        int cy = 2 + mapSize / 2;
        double yaw = Math.toRadians(game.yaw());
        // Minecraft's yaw: 0 = south (down on the map), 90 = west (left).
        double dx = -Math.sin(yaw);
        double dy = Math.cos(yaw);
        for (int i = 6; i >= 0; i--) {
            int x = (int) Math.round(cx + dx * i);
            int y = (int) Math.round(cy + dy * i);
            int s = i > 4 ? 0 : 1;
            r.rect(x - s, y - s, x + s + 1, y + s + 1, i > 4 ? 0xFFFFFFFF : UiKit.accent() | 0xFF000000);
        }
        r.rect(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF07060E);
        r.rect(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    /** A pretend map for the HUD editor (title screen, no world loaded). */
    private void drawPreview(Renderer r, int cells) {
        for (int cz = 0; cz < cells; cz++) {
            for (int cx = 0; cx < cells; cx++) {
                double wave = Math.sin(cx * 0.25) + Math.cos(cz * 0.2);
                int rgb = wave > 1.1 ? 0x4060C0 : wave > -0.6 ? 0x5C9A3A : 0xB8A86A;
                r.rect(2 + cx * CELL, 2 + cz * CELL, 2 + (cx + 1) * CELL, 2 + (cz + 1) * CELL,
                        0xFF000000 | shade(rgb, (cx + cz) % 7 == 0 ? 0.85 : 1.0));
            }
        }
    }

    private static int shade(int rgb, double factor) {
        int red = Math.min(255, (int) (((rgb >> 16) & 255) * factor));
        int green = Math.min(255, (int) (((rgb >> 8) & 255) * factor));
        int blue = Math.min(255, (int) ((rgb & 255) * factor));
        return (red << 16) | (green << 8) | blue;
    }
}
