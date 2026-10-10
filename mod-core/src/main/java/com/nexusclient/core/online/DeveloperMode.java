package com.nexusclient.core.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * DeveloperMode – is the player in this game the Nexus developer (the owner)?
 *
 * When the game starts, this asks the Nexus server (GET /api/me with the sign-in the launcher handed
 * over in NEXUS_TOKEN) what this player's role is. Only "DEVELOPER" switches on the in-game developer
 * tools (the "Developer" category in the Right Shift menu). The server decides – a player can't make
 * themselves the developer, and the tools only show information anyway.
 *
 * Developer test: -Dnexus.devTools=true switches it on without a server (self-tests).
 */
public final class DeveloperMode {

    private static volatile boolean developer;
    private static volatile boolean checked;

    private DeveloperMode() {}

    /** True once the server has confirmed this player is the Nexus developer. */
    public static boolean active() {
        return developer || Boolean.getBoolean("nexus.devTools");
    }

    /** True once the server answered (or there's no server to ask). */
    public static boolean checked() {
        return checked;
    }

    /** Asks the server, in the background. Called once when the game starts. */
    public static void check() {
        final String token = System.getenv("NEXUS_TOKEN");
        final String backend = System.getenv("NEXUS_BACKEND");
        if (token == null || token.isEmpty() || backend == null) {
            checked = true;
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(backend + "/api/me").openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    InputStream in = connection.getInputStream();
                    try {
                        JsonObject me = new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonObject();
                        developer = me.has("role") && "DEVELOPER".equals(me.get("role").getAsString());
                    } finally {
                        in.close();
                    }
                } catch (Exception e) {
                    // No server or not signed in – no developer tools.
                } finally {
                    checked = true;
                }
            }
        }, "Nexus developer check");
        thread.setDaemon(true);
        thread.start();
    }
}
