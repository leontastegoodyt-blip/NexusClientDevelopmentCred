package com.nexusclient.core.map;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
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
 * Also drawn: you (an arrow pointing where you face), waypoints, a compass (N on a tab, E S W) and
 * optional chunk lines, in a neon frame (soft glow, logo-gradient edge, corner brackets) with a
 * coordinates pill underneath. "Map range" shows 1x–5x as much ground.
 * Server-blockable (some servers ban minimaps).
 */
public class Minimap extends HudModule {

    public final NumberSetting size = add(new NumberSetting("size", "Size", 96, 64, 192, 8, ""));
    /** How much ground the map shows: 1x = 48 blocks across a 96-pixel map, 5x = 240. */
    public final NumberSetting range = add(new NumberSetting("range", "Map range", 1, 1, 5, 0.25, "x"));
    public final BooleanSetting chunkGrid = add(new BooleanSetting("chunkGrid", "Chunk grid", false));
    public final BooleanSetting showCoords = add(new BooleanSetting("coords", "Show coordinates", true));
    public final BooleanSetting showWaypoints = add(new BooleanSetting("waypoints", "Show waypoints", true));

    /** Extra blocks kept around the visible area, so the map can slide smoothly between updates. */
    private static final int MARGIN = 6;
    /** How often a few rows are looked up again (to show placed/broken blocks), in milliseconds. */
    private static final long RESCAN_EVERY = 60;
    private static final int RESCAN_ROWS = 6;
    private static final int UNKNOWN = 0xFF0E0E16;
    /** The frame around the map, in pixels. */
    private static final int FRAME = 3;

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
        int box = mapSize + 2 * FRAME;
        int footer = showCoords.isOn() ? 16 : 0;
        int mid = FRAME + mapSize / 2;

        drawFrame(r, box);

        // The map itself (clipped to its window).
        r.enableClip(FRAME, FRAME, FRAME + mapSize, FRAME + mapSize);
        double scale = 1 / blocksPerPixel();
        if (preview || !game.inWorld()) {
            drawPreview(r, mapSize);
        } else {
            update(game, (int) Math.ceil(mapSize * blocksPerPixel()));
            // Slide the picture so you're exactly in the middle (smooth, not block by block).
            float left = (float) (mid - (game.x() - originX) * scale);
            float top = (float) (mid - (game.z() - originZ) * scale);
            r.push(left, top, (float) scale);
            r.picture("minimap", pixels, n, n, version, 0, 0, n, n);
            r.pop();
            if (chunkGrid.isOn()) {
                drawChunkGrid(r, game, mapSize, scale);
            }
        }
        drawInnerShade(r, mapSize);
        if (!preview && game.inWorld()) {
            drawWaypoints(r, game, mapSize, scale);
        }
        r.disableClip();

        drawCornerBrackets(r, mapSize);
        drawCompass(r, mapSize);
        drawPlayer(r, preview ? 135 : game.yaw(), mid, mid);

        if (footer > 0) {
            drawFooter(r, game, preview, box);
        }
        return new int[] {box, box + footer};
    }

    /** How many blocks one map pixel shows: "Map range" 1x = half a block, 5x = two and a half. */
    private double blocksPerPixel() {
        return 0.5 * range.get();
    }

    // ------------------------------------------------------------------ the frame

    /** A soft neon glow, a dark rounded body and the Nexus gradient edge. */
    private void drawFrame(Renderer r, int box) {
        int accent = UiKit.accent() & 0xFFFFFF;
        // Glow: three faint rings outside the frame, fading out.
        int[] glow = {0x30, 0x18, 0x0A};
        for (int i = 0; i < glow.length; i++) {
            UiKit.roundOutline(r, -1 - i, -1 - i, box + 1 + i, box + 1 + i, 7 + i, (glow[i] << 24) | accent);
        }
        UiKit.round(r, 0, 0, box, box, 6, 0xEA07070D);
        UiKit.roundOutline(r, 0, 0, box, box, 6, (0x90 << 24) | accent);
        // The edge brightens into the logo gradient at the top and fades at the bottom.
        UiKit.gradientLine(r, 5, 0, box - 5, 0xFF);
        UiKit.gradientLine(r, 5, box - 1, box - 5, 0x70);
    }

    /** Darkens the map's edges a little, so it looks set into the frame. */
    private void drawInnerShade(Renderer r, int mapSize) {
        int x1 = FRAME;
        int y1 = FRAME;
        int x2 = FRAME + mapSize;
        int y2 = FRAME + mapSize;
        int[] alpha = {0x70, 0x40, 0x20, 0x0C};
        for (int i = 0; i < alpha.length; i++) {
            int c = alpha[i] << 24;
            r.rect(x1, y1 + i, x2, y1 + i + 1, c);                                // top
            r.rect(x1, y2 - i - 1, x2, y2 - i, c);                                // bottom
            r.rect(x1 + i, y1 + alpha.length, x1 + i + 1, y2 - alpha.length, c);  // left
            r.rect(x2 - i - 1, y1 + alpha.length, x2 - i, y2 - alpha.length, c);  // right
        }
    }

    /** Small L-shaped marks in each corner of the map, coloured along the logo gradient. */
    private void drawCornerBrackets(Renderer r, int mapSize) {
        int len = Math.max(5, mapSize / 12);
        int x1 = FRAME + 2;
        int y1 = FRAME + 2;
        int x2 = FRAME + mapSize - 2;
        int y2 = FRAME + mapSize - 2;
        int topLeft = 0xFF000000 | UiKit.gradient(0);
        int topRight = 0xFF000000 | UiKit.gradient(0.45);
        int bottomLeft = 0xFF000000 | UiKit.gradient(0.6);
        int bottomRight = 0xFF000000 | UiKit.gradient(1);
        r.rect(x1, y1, x1 + len, y1 + 1, topLeft);
        r.rect(x1, y1, x1 + 1, y1 + len, topLeft);
        r.rect(x2 - len, y1, x2, y1 + 1, topRight);
        r.rect(x2 - 1, y1, x2, y1 + len, topRight);
        r.rect(x1, y2 - 1, x1 + len, y2, bottomLeft);
        r.rect(x1, y2 - len, x1 + 1, y2, bottomLeft);
        r.rect(x2 - len, y2 - 1, x2, y2, bottomRight);
        r.rect(x2 - 1, y2 - len, x2, y2, bottomRight);
    }

    /** N (red, on a little tab over the top edge), and E, S, W along the other edges. */
    private void drawCompass(Renderer r, int mapSize) {
        int mid = FRAME + mapSize / 2;
        UiKit.round(r, mid - 6, -3, mid + 6, 9, 3, 0xF20A0A12);
        UiKit.roundOutline(r, mid - 6, -3, mid + 6, 9, 3, 0xFFFF6B5E);
        UiKit.centered(r, "N", mid + 1, -1, 0xFFFF6B5E, false);
        int dim = 0xC0B8B8CC;
        UiKit.centered(r, "S", mid + 1, FRAME + mapSize - 10, dim, true);
        r.text("W", FRAME + 3, mid - 4, dim, true);
        r.text("E", FRAME + mapSize - 3 - r.textWidth("E"), mid - 4, dim, true);
    }

    /** Coordinates and the way you face, in a small floating pill under the map. */
    private void drawFooter(Renderer r, GameInfo game, boolean preview, int box) {
        String coords = preview ? "128  64  -303"
                : (int) Math.floor(game.x()) + "  " + (int) Math.floor(game.y()) + "  " + (int) Math.floor(game.z());
        String facing = facing(preview ? 135 : game.yaw());
        int width = r.textWidth(coords) + r.textWidth(facing) + 18;
        int x1 = (box - width) / 2;
        int y1 = box + 3;
        UiKit.roundBox(r, x1, y1, x1 + width, y1 + 13, 4, 0xEA07070D, 0xFF26263E);
        UiKit.gradientLine(r, x1 + 4, y1, x1 + width - 4, 0xB0);
        r.text(facing, x1 + 6, y1 + 3, UiKit.accent() | 0xFF000000, false);
        r.text(coords, x1 + 12 + r.textWidth(facing), y1 + 3, UiKit.TEXT, false);
    }

    /** "N", "NE", "E"... from Minecraft's yaw (0 = south, 90 = west). */
    private static String facing(float yaw) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int index = (int) Math.floor(((yaw % 360 + 360) % 360 + 22.5) / 45) % 8;
        return names[index];
    }

    /** Faint lines on chunk borders (every 16 blocks). */
    private void drawChunkGrid(Renderer r, GameInfo game, int mapSize, double scale) {
        double half = mapSize / 2.0 / scale; // blocks from the middle to the edge
        int mid = FRAME + mapSize / 2;
        for (int axis = 0; axis < 2; axis++) {
            double centre = axis == 0 ? game.x() : game.z();
            for (int line = (int) Math.ceil((centre - half) / 16) * 16; line <= centre + half; line += 16) {
                int at = (int) Math.round(mid + (line - centre) * scale);
                if (axis == 0) {
                    r.rect(at, FRAME, at + 1, FRAME + mapSize, 0x22FFFFFF);
                } else {
                    r.rect(FRAME, at, FRAME + mapSize, at + 1, 0x22FFFFFF);
                }
            }
        }
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
        if (!showWaypoints.isOn() || waypoints == null) {
            return;
        }
        // Your waypoints (if Waypoints is on) and your party's pings (always).
        java.util.List<WaypointStore.Waypoint> list = new java.util.ArrayList<WaypointStore.Waypoint>();
        if (waypoints.isActive()) {
            list.addAll(waypoints.current(game));
        }
        list.addAll(waypoints.pings(game));
        for (WaypointStore.Waypoint waypoint : list) {
            if (!waypoint.visible) {
                continue;
            }
            double mx = mapSize / 2.0 + (waypoint.x + 0.5 - game.x()) * scale;
            double my = mapSize / 2.0 + (waypoint.z + 0.5 - game.z()) * scale;
            // Far away: keep it on the map's edge, in the right direction.
            mx = Math.max(3, Math.min(mapSize - 3, mx));
            my = Math.max(3, Math.min(mapSize - 3, my));
            int x = FRAME + (int) mx;
            int y = FRAME + (int) my;
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
                r.rect(FRAME + cx * 4, FRAME + cz * 4, FRAME + (cx + 1) * 4, FRAME + (cz + 1) * 4,
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
