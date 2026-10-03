package com.nexusclient.core.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.platform.GameInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ActivityReporter – earns clan honor. Once a minute it sends the Nexus server a summary of how the
 * player played: distance moved, how much they turned the camera, key presses, clicks, and seconds
 * spent in menus. The SERVER decides if that was real play or AFK (see the backend's AfkDetector), so
 * a changed client can't simply claim honor.
 *
 * Only works when the game was started by the Nexus launcher while signed in to Nexus (the launcher
 * passes the sign-in in the NEXUS_TOKEN environment variable).
 */
public final class ActivityReporter {

    public interface Notices {
        void show(String message);
    }

    private final String token = System.getenv("NEXUS_TOKEN");
    private final String backend = System.getenv("NEXUS_BACKEND");
    private final Notices notices;

    private double moved;
    private double turned;
    private int keyPresses;
    private int clicks;
    private int menuTicks;
    private int ticks;
    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private boolean[] lastKeys = new boolean[0];
    private int lastClicks;
    private boolean toldLimit;

    private static final String[] KEYS = {"forward", "back", "left", "right", "jump", "sneak", "attack", "use"};

    public ActivityReporter(Notices notices) {
        this.notices = notices;
    }

    public boolean enabled() {
        return token != null && !token.isEmpty() && backend != null;
    }

    /**
     * Called 20 times a second while in a world.
     *
     * @param totalClicks all clicks so far (from InputTracker)
     * @param menuOpen    true if a menu/screen is open
     */
    public void tick(GameInfo game, int totalClicks, boolean menuOpen) {
        if (!enabled() || !game.inWorld()) {
            return;
        }
        // Movement and turning since the last tick.
        double x = game.x();
        double y = game.y();
        double z = game.z();
        float yaw = game.yaw();
        if (!Double.isNaN(lastX)) {
            double step = Math.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ));
            if (step < 20) { // ignore teleports
                moved += step;
            }
            float turn = Math.abs(yaw - lastYaw) % 360;
            turned += Math.min(turn, 360 - turn);
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;

        // Key presses: count each time a movement/action key goes down.
        if (lastKeys.length != KEYS.length) {
            lastKeys = new boolean[KEYS.length];
        }
        for (int i = 0; i < KEYS.length; i++) {
            boolean down = game.isKeyDown(KEYS[i]);
            if (down && !lastKeys[i]) {
                keyPresses++;
            }
            lastKeys[i] = down;
        }
        clicks += Math.max(0, totalClicks - lastClicks);
        lastClicks = totalClicks;
        if (menuOpen) {
            menuTicks++;
        }

        if (++ticks >= 20 * 60) {
            sendAndReset();
        }
    }

    private void sendAndReset() {
        final JsonObject minute = new JsonObject();
        minute.addProperty("moveBlocks", Math.round(moved * 10) / 10.0);
        minute.addProperty("turnDegrees", Math.round(turned));
        minute.addProperty("keyPresses", keyPresses);
        minute.addProperty("clicks", clicks);
        minute.addProperty("chatMessages", 0);
        minute.addProperty("menuSeconds", menuTicks / 20);
        moved = turned = 0;
        keyPresses = clicks = menuTicks = ticks = 0;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                send(minute);
            }
        }, "Nexus activity");
        thread.setDaemon(true);
        thread.start();
    }

    private void send(JsonObject minute) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(backend + "/api/activity").openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(minute.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            if (connection.getResponseCode() != 200) {
                return;
            }
            InputStream in = connection.getInputStream();
            try {
                JsonObject result = new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonObject();
                if (!toldLimit && result.get("countedMinutes").getAsInt() >= result.get("dailyLimitMinutes").getAsInt()
                        && result.has("clanTag") && !result.get("clanTag").isJsonNull()) {
                    toldLimit = true;
                    notices.show("You've earned today's full clan honor (" + result.get("honorToday").getAsLong()
                            + "). Nice playing!");
                }
            } finally {
                in.close();
            }
        } catch (Exception e) {
            // The Nexus server is unreachable – try again next minute.
        }
    }
}
