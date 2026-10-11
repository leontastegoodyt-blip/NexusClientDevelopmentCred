package com.nexusclient.core.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Badges – the little icon in front of a player's name tag:
 *
 *   NEXUS      ⬡N  Name          they're using Nexus right now (blue N hexagon)
 *   PREMIUM    ⬡N+ Name          Nexus Premium (gold N hexagon with a "+")
 *   DEVELOPER  ⬡N  Name [DEV]    the Nexus owner
 *
 * Every 20 seconds this asks the Nexus server (GET /api/badges) about the players on the current
 * server, in the background. Each Minecraft version's name-tag code then asks {@link #of} and draws
 * the icon (with a font glyph on 1.20+, with shapes on 1.8.9).
 *
 * Only Nexus players see badges (only Nexus asks the server), just like Nexus cosmetics.
 *
 * Developer test: -Dnexus.badgeTest=DEVELOPER (or NEXUS / PREMIUM) gives EVERY player that badge, so
 * the self-tests can photograph it without a server.
 */
public final class Badges {

    public enum Badge { NEXUS, PREMIUM, DEVELOPER }

    /** The private-use characters of the badge font (assets/nexusclient/font/badges.json). */
    public static final String NEXUS_GLYPH = "";
    public static final String PREMIUM_GLYPH = "";
    public static final String DEV_TAG_GLYPH = "";

    private static final String BACKEND = System.getenv("NEXUS_BACKEND");
    private static final Map<UUID, Badge> badges = new ConcurrentHashMap<UUID, Badge>();
    private static int ticks;
    private static volatile boolean asking;

    private Badges() {}

    /** The badge for this player, or null if they have none. */
    public static Badge of(UUID player) {
        Badge test = parse(System.getProperty("nexus.badgeTest"));
        if (test != null) {
            return test;
        }
        return player == null ? null : badges.get(player);
    }

    /** How many nearby players the server says have a badge (for the developer debug HUD). */
    public static int known() {
        return badges.size();
    }

    /** The text that goes in front of a name for this badge ("" for none). */
    public static String prefix(Badge badge) {
        if (badge == null) {
            return "";
        }
        return (badge == Badge.PREMIUM ? PREMIUM_GLYPH : NEXUS_GLYPH) + " ";
    }

    /** The text that goes after a name for this badge ("" for none) – the developer's DEV tag. */
    public static String suffix(Badge badge) {
        return badge == Badge.DEVELOPER ? " " + DEV_TAG_GLYPH : "";
    }

    /**
     * Called every game tick. Every 20 seconds it asks the server about the players listed by
     * "players" (only worked out when it's time to ask, so it costs nothing the rest of the time).
     */
    public static void tick(Callable<Collection<UUID>> players) {
        if (BACKEND == null || asking || ++ticks % 400 != 1) {
            return;
        }
        final StringBuilder list = new StringBuilder();
        try {
            int count = 0;
            for (UUID uuid : players.call()) {
                if (count++ >= 200) {
                    break;
                }
                if (list.length() > 0) {
                    list.append(',');
                }
                list.append(uuid);
            }
        } catch (Exception e) {
            return; // not in a world yet
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
        }, "Nexus badges");
        thread.setDaemon(true);
        thread.start();
    }

    private static void refresh(String uuidList) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(BACKEND + "/api/badges?uuids=" + uuidList)
                    .openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            JsonObject answer;
            InputStream in = connection.getInputStream();
            try {
                answer = new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonObject();
            } finally {
                in.close();
            }
            Map<UUID, Badge> fresh = new ConcurrentHashMap<UUID, Badge>();
            for (Map.Entry<String, JsonElement> entry : answer.entrySet()) {
                Badge badge = parse(entry.getValue().getAsString());
                if (badge != null) {
                    fresh.put(UUID.fromString(entry.getKey()), badge);
                }
            }
            badges.clear();
            badges.putAll(fresh);
            refreshClanTags(uuidList);
        } catch (Exception e) {
            // The Nexus server is unreachable – keep the old badges and try again later.
        }
    }

    private static final Map<UUID, String> clanTags = new ConcurrentHashMap<UUID, String>();

    /**
     * The clan tag of this player (e.g. "APPLE"), or null – shown after their name on name tags
     * (switch off in Nexus Social's settings).
     */
    public static String clanTag(UUID player) {
        String test = System.getProperty("nexus.clanTagTest");
        if (test != null) {
            return test; // developer self-test: everyone is in this clan
        }
        if (player == null || !com.nexusclient.core.NexusCore.get().get(Social.class).clanTags.isOn()) {
            return null;
        }
        return clanTags.get(player);
    }

    /** Asks which clans the nearby players are in (public, like the badges). */
    private static void refreshClanTags(String uuidList) {
        try {
            JsonObject answer = NexusHttp.get("/api/clans/tags?uuids=" + uuidList).getAsJsonObject();
            Map<UUID, String> fresh = new ConcurrentHashMap<UUID, String>();
            for (Map.Entry<String, JsonElement> entry : answer.entrySet()) {
                String tag = entry.getValue().getAsString();
                if (tag.matches("[A-Z0-9]{2,5}")) {
                    fresh.put(UUID.fromString(entry.getKey()), tag);
                }
            }
            clanTags.clear();
            clanTags.putAll(fresh);
        } catch (Exception e) {
            // The Nexus server is unreachable – keep the old tags.
        }
    }

    private static Badge parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Badge.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
