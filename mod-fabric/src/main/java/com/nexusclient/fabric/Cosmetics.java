package com.nexusclient.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetics – shows Nexus capes on Nexus players (only players using Nexus can see them, because
 * only Nexus asks the Nexus server what everyone wears). Every 20 seconds it asks about the players
 * on the server, downloads cape pictures it hasn't got yet, and PlayerInfoMixin swaps the cape.
 * Wings, hats and emotes aren't drawn in-game yet (they need their own 3D models).
 */
public final class Cosmetics {

    private static final String BACKEND = System.getenv("NEXUS_BACKEND");
    private static final Map<UUID, String> capes = new ConcurrentHashMap<>();       // player → cape id
    private static final Map<String, ClientAsset.Texture> loaded = new ConcurrentHashMap<>(); // cape id → texture
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    private static int ticks;

    private Cosmetics() {}

    /** The Nexus cape texture this player wears, or null. Called by PlayerInfoMixin. */
    public static ClientAsset.Texture capeFor(UUID player) {
        String id = capes.get(player);
        return id == null ? null : loaded.get(id);
    }

    /** Called every tick (from NexusFabric). */
    public static void tick(Minecraft mc) {
        if (BACKEND == null || mc.getConnection() == null || ++ticks % 400 != 1) {
            return;
        }
        StringJoiner uuids = new StringJoiner(",");
        int count = 0;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (count++ >= 200) {
                break;
            }
            uuids.add(info.getProfile().id().toString());
        }
        String list = uuids.toString();
        if (list.isEmpty()) {
            return;
        }
        Thread thread = new Thread(() -> refresh(mc, list), "Nexus cosmetics");
        thread.setDaemon(true);
        thread.start();
    }

    private static void refresh(Minecraft mc, String uuidList) {
        try {
            JsonObject worn = JsonParser.parseString(get("/api/cosmetics/worn?uuids=" + uuidList)).getAsJsonObject();
            capes.clear();
            for (Map.Entry<String, JsonElement> entry : worn.entrySet()) {
                JsonObject items = entry.getValue().getAsJsonObject();
                if (items.has("CAPE")) {
                    String cape = items.get("CAPE").getAsString();
                    capes.put(UUID.fromString(entry.getKey()), cape);
                    if (!loaded.containsKey(cape) && loading.add(cape)) {
                        loadTexture(mc, cape);
                    }
                }
            }
        } catch (Exception e) {
            // The Nexus server is unreachable – try again later.
        }
    }

    /** Downloads a cape picture and hands it to Minecraft (on the game thread). */
    private static void loadTexture(Minecraft mc, String id) throws Exception {
        if (!id.matches("[a-z0-9_]{1,32}")) {
            return;
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(BACKEND + "/cosmetics/" + id + ".png").toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        NativeImage image;
        try (InputStream in = connection.getInputStream()) {
            image = NativeImage.read(in);
        }
        mc.execute(() -> {
            Identifier location = Identifier.fromNamespaceAndPath("nexusclient", "cosmetics/" + id);
            mc.getTextureManager().register(location, new DynamicTexture(() -> "Nexus cosmetic " + id, image));
            loaded.put(id, new ClientAsset.ResourceTexture(location, location));
        });
    }

    private static String get(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(BACKEND + path).toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
