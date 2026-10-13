package com.nexusclient.core.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capes – Nexus capes on Nexus players (1.8.9 and 1.20–1.21; 26.3 has its own Cosmetics class).
 *
 * Every 20 seconds this asks the Nexus server (GET /api/cosmetics/worn, public) which cape each nearby
 * player wears, and downloads the pictures it hasn't got yet (/cosmetics/<id>.png) – all in the
 * background. Each Minecraft version then turns a picture into a texture the first time it's needed
 * and shows it instead of the player's normal cape (and elytra). Only Nexus players see Nexus capes,
 * because only Nexus asks.
 *
 * The same answer also says who wears a Nexus hat (Halo) or wings (Neon Wings). Those aren't
 * pictures: they're glowing shapes drawn in code (CosmeticShapes) on top of the player model.
 * Developer test: -Dnexus.cosmeticTest=true puts the halo and wings on every player.
 */
public final class Capes {

    private static final String BACKEND = System.getenv("NEXUS_BACKEND");
    private static final Map<UUID, String> worn = new ConcurrentHashMap<UUID, String>();     // player → cape id
    private static final Map<String, byte[]> pictures = new ConcurrentHashMap<String, byte[]>(); // cape id → PNG
    private static final Map<UUID, String> hats = new ConcurrentHashMap<UUID, String>();     // player → hat id (e.g. "halo")
    private static final Map<UUID, String> wings = new ConcurrentHashMap<UUID, String>();    // player → wings id
    private static final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private static int ticks;
    private static volatile boolean asking;

    private Capes() {}

    /** The Nexus cape this player wears, or null. */
    public static String capeOf(UUID player) {
        if (System.getProperty("nexus.capeTest") != null) {
            return "selftest"; // developer self-test: everyone wears the test cape
        }
        return player == null ? null : worn.get(player);
    }

    /** The Nexus hat this player wears (drawn in code by CosmeticShapes), or null. */
    public static String hatOf(UUID player) {
        if (System.getProperty("nexus.cosmeticTest") != null) {
            return "halo"; // developer self-test: everyone wears the halo
        }
        return player == null ? null : hats.get(player);
    }

    /** The Nexus wings this player wears (drawn in code by CosmeticShapes), or null. */
    public static String wingsOf(UUID player) {
        if (System.getProperty("nexus.cosmeticTest") != null) {
            return "neon_wings";
        }
        return player == null ? null : wings.get(player);
    }

    /**
     * Remembers the hats and wings in an answer from /api/cosmetics/worn ({"<uuid>": {"HAT": "halo", …}}).
     * Used by this class and by 26.3's own Cosmetics class (which handles its capes itself).
     */
    public static void rememberShapes(JsonObject answer) {
        Map<UUID, String> freshHats = new ConcurrentHashMap<UUID, String>();
        Map<UUID, String> freshWings = new ConcurrentHashMap<UUID, String>();
        for (Map.Entry<String, JsonElement> entry : answer.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject items = entry.getValue().getAsJsonObject();
                if (items.has("HAT") && items.get("HAT").getAsString().matches("[a-z0-9_]{1,32}")) {
                    freshHats.put(uuid, items.get("HAT").getAsString());
                }
                if (items.has("WINGS") && items.get("WINGS").getAsString().matches("[a-z0-9_]{1,32}")) {
                    freshWings.put(uuid, items.get("WINGS").getAsString());
                }
            } catch (Exception e) {
                // one odd entry – skip it
            }
        }
        hats.clear();
        hats.putAll(freshHats);
        wings.clear();
        wings.putAll(freshWings);
    }

    /** The cape's picture (a PNG file), or null if it hasn't downloaded yet. */
    public static byte[] picture(String capeId) {
        String test = System.getProperty("nexus.capeTest");
        if (test != null && capeId.equals("selftest") && !pictures.containsKey(capeId)) {
            try {
                pictures.put(capeId, java.nio.file.Files.readAllBytes(new java.io.File(test).toPath()));
            } catch (java.io.IOException e) {
                return null;
            }
        }
        return pictures.get(capeId);
    }

    /** Called every tick; asks about the players near us every 20 seconds. */
    public static void tick(Callable<Collection<UUID>> players) {
        if (BACKEND == null || asking || ++ticks % 400 != 3) {
            return;
        }
        final StringBuilder list = new StringBuilder();
        try {
            int count = 0;
            for (UUID uuid : players.call()) {
                if (count++ >= 200) {
                    break;
                }
                list.append(list.length() > 0 ? "," : "").append(uuid);
            }
        } catch (Exception e) {
            return;
        }
        if (list.length() == 0) {
            return;
        }
        asking = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    refresh(list.toString());
                } finally {
                    asking = false;
                }
            }
        }, "Nexus capes");
        thread.setDaemon(true);
        thread.start();
    }

    private static void refresh(String uuidList) {
        try {
            JsonObject answer = new JsonParser().parse(new InputStreamReader(
                    open("/api/cosmetics/worn?uuids=" + uuidList), "UTF-8")).getAsJsonObject();
            Map<UUID, String> fresh = new ConcurrentHashMap<UUID, String>();
            for (Map.Entry<String, JsonElement> entry : answer.entrySet()) {
                JsonObject items = entry.getValue().getAsJsonObject();
                if (items.has("CAPE")) {
                    String cape = items.get("CAPE").getAsString();
                    if (cape.matches("[a-z0-9_]{1,32}")) {
                        fresh.put(UUID.fromString(entry.getKey()), cape);
                        if (!pictures.containsKey(cape) && downloading.add(cape)) {
                            download(cape);
                        }
                    }
                }
            }
            worn.clear();
            worn.putAll(fresh);
            rememberShapes(answer);
        } catch (Exception e) {
            // The Nexus server is unreachable – try again later.
        }
    }

    private static void download(String cape) {
        try {
            InputStream in = open("/cosmetics/" + cape + ".png");
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    bytes.write(buffer, 0, read);
                }
                pictures.put(cape, bytes.toByteArray());
            } finally {
                in.close();
            }
        } catch (Exception e) {
            downloading.remove(cape); // try again next time
        }
    }

    private static InputStream open(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BACKEND + path).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        return connection.getInputStream();
    }
}
