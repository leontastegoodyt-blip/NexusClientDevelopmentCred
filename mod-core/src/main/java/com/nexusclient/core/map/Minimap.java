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
 * How it stays fast:
 *   - The map is ONE picture (one pixel per block), kept on the graphics card (Renderer.picture), so
 *     drawing it each frame costs about as much as one rectangle – not thousands of little squares.
 *   - The picture is a bit bigger than the map window. While you walk it just slides (smoothly, by
 *     parts of a block); when you've moved a whole block, the pixels are shifted and only the new
 *     strip at the edge is looked up in the world.
 *   - A few rows are looked up again every so often, so placed and broken blocks show up too.
 *
 * Each pixel is coloured like the top block there (Minecraft's own map colours), lit from the
 * north-west: slopes facing up-left are lighter, slopes facing down-right darker, so hills show.
 * Also drawn: you (an arrow pointing where you face), waypoints, and "N" for north.
 * Server-blockable (some servers ban minimaps).
 */
public class Minimap extends HudModule {

    public final NumberSetting size = add(new NumberSetting("size", "Size", 96, 64, 192, 8, ""));
    public final ChoiceSetting zoom = add(new ChoiceSetting("zoom", "Zoom", "1 block", "1 block", "2 blocks", "4 blocks"));
    public final BooleanSetting showCoords = add(new BooleanSetting("coords", "Show coordinates", true));
    public final BooleanSetting showWaypoints = add(new BooleanSetting("waypoints", "Show waypoints", true));

    /** Extra blocks kept around the visible area, so the map can slide smoothly between updates. */
    private static final int MARGIN = 6;
    /** How often a few rows are looked up again (to show placed/broken blocks), in milliseconds. */
    private static final long RESCAN_EVERY = 60;
    private static final int RESCAN_ROWS = 6;
    private static final int UNKNOWN = 0xFF0E0E16;

    // The picture: n x n blocks, its top-left corner at world block (originX, originZ).
    private int n;
    private int originX;
    private int originZ;
    private int[] colour = new int[0]; // the top block's map colour (0 = nothing / not loaded)
    private int[] height = new int[0];
    private int[] pixels = new int[0];
    private int version;
    private boolean dirty;
    private String world = "";
    private int rescanRow;
    private long lastRescan;

    public Minimap() {
        super("minimap", "Minimap", "A small map of the area around you.", Category.VISUAL, false, true, 0.82, 0.02);
    }

    @Override
    protected int[] draw(Renderer r, GameInfo game, boolean preview) {
        int mapSize = size.get().intValue();
        int footer = showCoords.isOn() ? 12 : 0;

        // The frame: rounded, with the Nexus gradient line along the top.
        UiKit.roundBox(r, 0, 0, mapSize + 4, mapSize + 4 + footer, 4, 0xE00A0A12, 0xFF26263E);

        r.enableClip(2, 2, 2 + mapSize, 2 + mapSize);
        if (preview || !game.inWorld()) {
            drawPreview(r, mapSize);
        } else {
            double blocksPerPixel = zoom.is("4 blocks") ? 2 : zoom.is("2 blocks") ? 1 : 0.5;
            update(game, (int) Math.ceil(mapSize * blocksPerPixel));
            // Slide the picture so you're exactly in the middle (smooth, not block by block).
            double scale = 1 / blocksPerPixel;
            float left = (float) (2 + mapSize / 2.0 - (game.x() - originX) * scale);
            float top = (float) (2 + mapSize / 2.0 - (game.z() - originZ) * scale);
            r.push(left, top, (float) scale);
            r.picture("minimap", pixels, n, n, version, 0, 0, n, n);
            r.pop();
            drawWaypoints(r, game, mapSize, scale);
        }
        r.disableClip();
        UiKit.gradientLine(r, 4, 0, mapSize - 4, 0xD0);
        drawPlayer(r, preview ? 135 : game.yaw(), 2 + mapSize / 2f, 2 + mapSize / 2f);

        // North marker.
        UiKit.round(r, mapSize / 2 - 2, 3, mapSize / 2 + 7, 12, 2, 0xC0101018);
        r.text("N", mapSize / 2, 4, 0xFFFF6B5E, false);
        if (footer > 0) {
            String coords = preview ? "128, 64, -303"
                    : (int) Math.floor(game.x()) + ", " + (int) Math.floor(game.y()) + ", " + (int) Math.floor(game.z());
            UiKit.centered(r, coords, (mapSize + 4) / 2, mapSize + 6, UiKit.TEXT, false);
        }
        return new int[] {mapSize + 4, mapSize + 4 + footer};
    }

    // ------------------------------------------------------------------ keeping the picture up to date

    /** Keeps the picture centred on you; looks up only what's new. */
    private void update(GameInfo game, int visibleBlocks) {
        int wanted = visibleBlocks + 2 * MARGIN;
        int px = (int) Math.floor(game.x());
        int pz = (int) Math.floor(game.z());
        String here = game.worldKey() + "/" + game.dimension();
        if (wanted != n || !here.equals(world)) {
            // New size, zoom, world or dimension: start again.
            n = wanted;
            world = here;
            colour = new int[n * n];
            height = new int[n * n];
            pixels = new int[n * n];
            originX = px - n / 2;
            originZ = pz - n / 2;
            lookUp(game, 0, 0, n, n);
            dirty = true;
        } else {
            int dx = px - n / 2 - originX;
            int dz = pz - n / 2 - originZ;
            if (Math.abs(dx) >= n || Math.abs(dz) >= n) {
                originX += dx; // teleported: everything is new
                originZ += dz;
                lookUp(game, 0, 0, n, n);
                dirty = true;
            } else if (Math.abs(dx) >= MARGIN / 2 || Math.abs(dz) >= MARGIN / 2) {
                shift(dx, dz);
                // Look up only the strips that came into view.
                if (dx > 0) lookUp(game, n - dx, 0, n, n);
                if (dx < 0) lookUp(game, 0, 0, -dx, n);
                if (dz > 0) lookUp(game, 0, n - dz, n, n);
                if (dz < 0) lookUp(game, 0, 0, n, -dz);
            }
        }
        // A few rows again now and then, so changes to the world show up.
        long now = System.currentTimeMillis();
        if (now - lastRescan >= RESCAN_EVERY) {
            lastRescan = now;
            int from = rescanRow % n;
            lookUp(game, 0, from, n, Math.min(n, from + RESCAN_ROWS));
            rescanRow = (from + RESCAN_ROWS) % n;
        }
        if (dirty) {
            paint();
            dirty = false;
            version++;
        }
    }

    /** Moves the picture's contents by (dx, dz) blocks (what's already known stays). */
    private void shift(int dx, int dz) {
        int[] oldColour = colour;
        int[] oldHeight = height;
        colour = new int[n * n];
        height = new int[n * n];
        for (int z = 0; z < n; z++) {
            int fromZ = z + dz;
            if (fromZ < 0 || fromZ >= n) {
                continue;
            }
            for (int x = 0; x < n; x++) {
                int fromX = x + dx;
                if (fromX >= 0 && fromX < n) {
                    colour[z * n + x] = oldColour[fromZ * n + fromX];
                    height[z * n + x] = oldHeight[fromZ * n + fromX];
                }
            }
        }
        originX += dx;
        originZ += dz;
        dirty = true;
    }

    /** Asks the world for the top block of every column in a part of the picture. */
    private void lookUp(GameInfo game, int x1, int z1, int x2, int z2) {
        for (int z = z1; z < z2; z++) {
            for (int x = x1; x < x2; x++) {
                int wx = originX + x;
                int wz = originZ + z;
                int i = z * n + x;
                int newColour = game.mapColour(wx, wz);
                int newHeight = game.surfaceY(wx, wz);
                if (newColour != colour[i] || newHeight != height[i]) {
                    colour[i] = newColour;
                    height[i] = newHeight;
                    dirty = true; // only re-send the picture when something really changed
                }
            }
        }
    }

    /** Turns colours + heights into the finished pixels, lit from the north-west. */
    private void paint() {
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int i = z * n + x;
                int rgb = colour[i];
                if (rgb == 0) {
                    pixels[i] = UNKNOWN;
                    continue;
                }
                int h = height[i];
                int north = z > 0 && colour[i - n] != 0 ? height[i - n] : h;
                int west = x > 0 && colour[i - 1] != 0 ? height[i - 1] : h;
                int slope = Math.max(-4, Math.min(4, (h - north) + (h - west)));
                pixels[i] = 0xFF000000 | shade(rgb, 1.0 + slope * 0.07);
            }
        }
    }

    // ------------------------------------------------------------------ drawing on top

    private void drawWaypoints(Renderer r, GameInfo game, int mapSize, double scale) {
        Waypoints waypoints = NexusCore.get().get(Waypoints.class);
        if (!showWaypoints.isOn() || waypoints == null || !waypoints.isActive()) {
            return;
        }
        for (WaypointStore.Waypoint waypoint : waypoints.current(game)) {
            if (!waypoint.visible) {
                continue;
            }
            double mx = mapSize / 2.0 + (waypoint.x + 0.5 - game.x()) * scale;
            double my = mapSize / 2.0 + (waypoint.z + 0.5 - game.z()) * scale;
            // Far away: keep it on the map's edge, in the right direction.
            mx = Math.max(3, Math.min(mapSize - 3, mx));
            my = Math.max(3, Math.min(mapSize - 3, my));
            int x = 2 + (int) mx;
            int y = 2 + (int) my;
            UiKit.round(r, x - 3, y - 3, x + 3, y + 3, 2, 0xFF07060E);
            UiKit.round(r, x - 2, y - 2, x + 2, y + 2, 1, 0xFF000000 | waypoint.colour);
        }
    }

    /**
     * You: an arrow pointing the way you face, drawn at double resolution (half pixels) so its
     * edges look smooth. Minecraft's yaw: 0 = south (down on the map), 90 = west (left).
     */
    private void drawPlayer(Renderer r, float yaw, float cx, float cy) {
        double angle = Math.toRadians(yaw);
        double fx = -Math.sin(angle); // forward
        double fy = Math.cos(angle);
        double sx = -fy;              // sideways
        double sy = fx;
        r.push(cx, cy, 0.5f);
        for (int pass = 0; pass < 2; pass++) {
            double grow = pass == 0 ? 2.2 : 0; // first a dark outline, then the arrow itself
            int argb = pass == 0 ? 0xE0050508 : 0xFFFFFFFF;
            for (int y = -12; y <= 12; y++) {
                // Each row is drawn as a few strips (not pixel by pixel), to keep it cheap.
                int start = Integer.MIN_VALUE;
                for (int x = -12; x <= 13; x++) {
                    // Position along (forward) and across (side) the arrow, in half pixels.
                    double along = x * fx + y * fy;
                    double across = Math.abs(x * sx + y * sy);
                    boolean inside = x <= 12 && along <= 9 + grow && along >= -7 - grow
                            && across <= (9 + grow - along) * 0.5
                            && !(along < -2 && across < (-2 - along) * 0.9 - grow * 0.5); // the notch at the back
                    if (inside && start == Integer.MIN_VALUE) {
                        start = x;
                    } else if (!inside && start != Integer.MIN_VALUE) {
                        r.rect(start, y, x, y + 1, argb);
                        start = Integer.MIN_VALUE;
                    }
                }
            }
        }
        r.pop();
    }

    /** A pretend map for the HUD editor (title screen, no world loaded). */
    private void drawPreview(Renderer r, int mapSize) {
        int cells = mapSize / 4;
        for (int cz = 0; cz < cells; cz++) {
            for (int cx = 0; cx < cells; cx++) {
                double wave = Math.sin(cx * 0.35) + Math.cos(cz * 0.3);
                int rgb = wave > 1.1 ? 0x4060C0 : wave > -0.6 ? 0x5C9A3A : 0xB8A86A;
                r.rect(2 + cx * 4, 2 + cz * 4, 2 + (cx + 1) * 4, 2 + (cz + 1) * 4,
                        0xFF000000 | shade(rgb, (cx + cz) % 7 == 0 ? 0.88 : 1.0));
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
