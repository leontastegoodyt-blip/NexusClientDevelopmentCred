package com.nexusclient.core.map;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.Overlay;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.NumberSetting;
import com.nexusclient.core.ui.UiKit;

import java.util.List;
import java.util.Locale;

/**
 * Waypoints – mark places and see them through walls, with their name and distance.
 *
 *   B (changeable in Controls)   adds a waypoint where you stand
 *   Right Shift → Waypoints      rename-free list: show/hide or delete each one
 *
 * The markers are drawn on the screen (not in the 3D world): each frame we work out where the
 * waypoint would appear from where the camera is and which way it looks ("projection" – the same
 * maths a 3D game uses), and draw a small diamond with the name and distance there. Waypoints behind
 * you or off to the side slide along the screen edge so you always know which way to turn.
 *
 * Server-blockable: servers can switch it off (the project brief – some servers ban waypoints).
 */
public class Waypoints extends Module implements Overlay {

    public final BooleanSetting showDistance = add(new BooleanSetting("distance", "Show distance", true));
    public final BooleanSetting deathPoint = add(new BooleanSetting("death", "Add a waypoint where I die", true));
    public final NumberSetting maxDistance = add(new NumberSetting("maxDistance", "Hide when further than", 5000, 100, 10000, 100, "m"));

    private final WaypointStore store = new WaypointStore();
    private boolean wasDead;

    public Waypoints() {
        super("waypoints", "Waypoints", "Mark places and see them through walls with their distance.", Category.VISUAL, false, true);
    }

    /** Called by NexusCore once the config folder is known. */
    public void load(java.io.File configFolder) {
        store.load(configFolder);
    }

    public WaypointStore store() {
        return store;
    }

    /** The waypoints of the world and dimension the player is in now. */
    public List<WaypointStore.Waypoint> current(GameInfo game) {
        return store.list(game.worldKey(), game.dimension());
    }

    /** A party ping: a marker that disappears by itself (not saved). */
    private static final class Ping {
        final WaypointStore.Waypoint waypoint;
        final String dimension;
        final long until;

        Ping(WaypointStore.Waypoint waypoint, String dimension, long until) {
            this.waypoint = waypoint;
            this.dimension = dimension;
            this.until = until;
        }
    }

    private final List<Ping> pings = new java.util.ArrayList<Ping>();

    /**
     * Adds a party ping (online/Social): shown for {@code millis} even when Waypoints is switched off,
     * unless the server blocks waypoints. A new ping from the same player replaces their old one.
     */
    public void addPing(String from, int colour, double x, double y, double z, String dimension, long millis) {
        WaypointStore.Waypoint waypoint = new WaypointStore.Waypoint(from, (int) Math.floor(x), (int) Math.floor(y),
                (int) Math.floor(z), colour);
        for (int i = pings.size() - 1; i >= 0; i--) {
            if (pings.get(i).waypoint.name.equals(from)) {
                pings.remove(i);
            }
        }
        pings.add(new Ping(waypoint, dimension, System.currentTimeMillis() + millis));
    }

    /** The party pings in the dimension the player is in now (old ones are dropped). */
    public List<WaypointStore.Waypoint> pings(GameInfo game) {
        List<WaypointStore.Waypoint> here = new java.util.ArrayList<WaypointStore.Waypoint>();
        if (isBlockedByServer()) {
            return here;
        }
        long now = System.currentTimeMillis();
        for (int i = pings.size() - 1; i >= 0; i--) {
            Ping ping = pings.get(i);
            if (ping.until < now) {
                pings.remove(i);
            } else if (ping.dimension.equals(game.dimension())) {
                here.add(ping.waypoint);
            }
        }
        return here;
    }

    /** The "Add waypoint" key: a waypoint where you stand. */
    public void addHere(GameInfo game) {
        if (!game.inWorld() || !isActive()) {
            return;
        }
        WaypointStore.Waypoint waypoint = store.add(game.worldKey(), game.dimension(), null,
                (int) Math.floor(game.x()), (int) Math.floor(game.y()), (int) Math.floor(game.z()));
        NexusCore.get().platform().showNotice("Waypoint \"" + waypoint.name + "\" added at "
                + waypoint.x + " " + waypoint.y + " " + waypoint.z);
    }

    @Override
    public void onTick(GameInfo game) {
        // A death waypoint: the moment health drops to 0.
        boolean dead = game.inWorld() && game.health() <= 0;
        if (dead && !wasDead && deathPoint.isOn()) {
            List<WaypointStore.Waypoint> list = current(game);
            // Only keep the latest death point.
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).name.equals("Death")) {
                    list.remove(i);
                }
            }
            WaypointStore.Waypoint death = store.add(game.worldKey(), game.dimension(), "Death",
                    (int) Math.floor(game.x()), (int) Math.floor(game.y()), (int) Math.floor(game.z()));
            death.colour = 0xFF6B5E;
            store.save();
        }
        wasDead = dead;
    }

    // ------------------------------------------------------------------ drawing the markers

    @Override
    public void drawOverlay(Renderer r, GameInfo game) {
        drawMarkers(r, game, current(game), maxDistance.get());
    }

    /** Draws markers for a list of waypoints (also used for party pings, see online/Social). */
    public void drawMarkers(Renderer r, GameInfo game, List<WaypointStore.Waypoint> list, double maxDistance) {
        if (!game.inWorld() || game.menuOpen()) {
            return;
        }
        double eyeX = game.x();
        double eyeY = game.eyeY();
        double eyeZ = game.z();
        double yaw = Math.toRadians(game.yaw());
        double pitch = Math.toRadians(game.pitch());
        // Which way the camera looks (forward), and its right and up directions.
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        // up = right × forward
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double focal = (r.height() / 2.0) / Math.tan(Math.toRadians(game.fov()) / 2);

        for (WaypointStore.Waypoint waypoint : list) {
            if (!waypoint.visible) {
                continue;
            }
            double dx = waypoint.x + 0.5 - eyeX;
            double dy = waypoint.y + 1.0 - eyeY;
            double dz = waypoint.z + 0.5 - eyeZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > maxDistance) {
                continue;
            }
            double depth = dx * fx + dy * fy + dz * fz;
            double side = dx * rx + dz * rz;
            double height = dx * ux + dy * uy + dz * uz;
            double sx;
            double sy;
            boolean onScreen = depth > 0.1;
            if (onScreen) {
                sx = r.width() / 2.0 + side / depth * focal;
                sy = r.height() / 2.0 - height / depth * focal;
                onScreen = sx >= 8 && sx <= r.width() - 8 && sy >= 8 && sy <= r.height() - 8;
            } else {
                sx = 0;
                sy = 0;
            }
            if (!onScreen) {
                // Slide along the screen edge, in the direction you'd have to turn.
                double angle = Math.atan2(-height, side);
                if (depth <= 0.1 && Math.abs(side) < 0.001) {
                    angle = Math.PI / 2; // straight behind: show it at the bottom
                }
                double cx = r.width() / 2.0;
                double cy = r.height() / 2.0;
                double scale = Math.min((cx - 10) / Math.max(0.001, Math.abs(Math.cos(angle))),
                        (cy - 10) / Math.max(0.001, Math.abs(Math.sin(angle))));
                sx = cx + Math.cos(angle) * scale;
                sy = cy + Math.sin(angle) * scale;
            }
            drawMarker(r, (int) sx, (int) sy, waypoint, distance, onScreen);
        }
    }

    private void drawMarker(Renderer r, int x, int y, WaypointStore.Waypoint waypoint, double distance, boolean onScreen) {
        int colour = 0xFF000000 | waypoint.colour;
        // A diamond: rows of growing then shrinking width.
        for (int i = 0; i < 5; i++) {
            r.rect(x - i, y - 4 + i, x + i + 1, y - 3 + i, colour);
            r.rect(x - i, y + 4 - i, x + i + 1, y + 5 - i, colour);
        }
        r.rect(x, y - 1, x + 1, y + 2, 0xFF07060E);
        String label = waypoint.name + (showDistance.isOn() ? "  " + formatDistance(distance) : "");
        int w = r.textWidth(label) + 8;
        int lx = Math.max(2, Math.min(x - w / 2, r.width() - w - 2));
        int ly = y - 18 < 2 ? y + 8 : y - 18;
        UiKit.round(r, lx, ly, lx + w, ly + 12, 3, onScreen ? 0xA0101018 : 0x70101018);
        r.text(label, lx + 4, ly + 2, onScreen ? 0xFFFFFFFF : 0xC0FFFFFF, false);
    }

    static String formatDistance(double metres) {
        return metres >= 1000 ? String.format(Locale.ROOT, "%.1fkm", metres / 1000) : Math.round(metres) + "m";
    }
}
