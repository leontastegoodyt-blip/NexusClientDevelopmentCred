package com.nexusclient.core.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.NexusCore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * SettingsSync – the in-game Nexus settings (config/nexus/nexus.json: HUD layout, mod switches,
 * colours…) follow the player to any PC, through their Nexus account (the project brief: "Settings sync").
 *
 *   - When the game starts, it asks the account for its copy. If that copy is NEWER (saved on another
 *     PC), it replaces the local settings. If the local one is newer, it's uploaded.
 *   - Every save is uploaded a few seconds later (several quick saves = one upload).
 *   - Nothing is uploaded until the start-up check has finished, so a brand-new PC (with default
 *     settings) never overwrites the player's real settings.
 * "savedAt" (when the settings were saved, in ms) decides which copy wins; the server also refuses
 * to replace a newer copy with an older one. Switch off in Nexus Settings → "Sync settings".
 */
public final class SettingsSync {

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Nexus settings sync");
            thread.setDaemon(true);
            return thread;
        }
    });

    private static final Object LOCK = new Object();
    private static String pendingData;   // settings waiting to be uploaded
    private static long pendingAt;
    private static boolean scheduled;
    private static volatile boolean ready; // the start-up check has finished
    private static volatile String status = "Not synced yet";

    private SettingsSync() {}

    public static boolean enabled() {
        return NexusHttp.signedIn() && NexusCore.get().settings().syncSettings.isOn();
    }

    /** For the settings page: what happened last ("Synced", "Loaded from your account"…). */
    public static String status() {
        return enabled() ? status : NexusHttp.signedIn() ? "Off" : "Needs the Nexus launcher (signed in)";
    }

    /** Called once when the game starts (after the local settings were loaded). */
    public static void start() {
        if (!enabled()) {
            ready = true;
            return;
        }
        NexusHttp.async(new Runnable() {
            public void run() {
                boolean applied = false;
                try {
                    JsonObject saved = NexusHttp.get("/api/me/mod-settings").getAsJsonObject();
                    final long remoteAt = saved.get("savedAt").getAsLong();
                    String data = saved.has("data") ? saved.get("data").getAsString() : "";
                    final long localAt = NexusCore.get().localSavedAt();
                    if (remoteAt > localAt && !data.isEmpty()) {
                        final JsonObject root = new JsonParser().parse(data).getAsJsonObject();
                        applied = true;
                        NexusCore.get().runLater(new Runnable() {
                            public void run() {
                                NexusCore.get().applySyncedSettings(root, remoteAt);
                                NexusCore.get().noticeLater("Loaded your Nexus settings from your account.");
                            }
                        });
                        status = "Loaded from your account";
                    } else if (localAt > remoteAt) {
                        // This PC has newer settings: send them (on the game thread, then uploaded).
                        NexusCore.get().runLater(new Runnable() {
                            public void run() {
                                NexusCore.get().save();
                            }
                        });
                    } else {
                        status = "Synced";
                    }
                } catch (Exception e) {
                    status = "Couldn't reach the Nexus server";
                } finally {
                    synchronized (LOCK) {
                        ready = true;
                        if (applied) {
                            pendingData = null; // the account's newer settings replace anything waiting
                        }
                    }
                    schedule();
                }
            }
        });
    }

    /** Called by NexusCore.save(): uploads these settings a few seconds from now. */
    public static void uploadLater(JsonObject root, long savedAt) {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            pendingData = root.toString(); // a snapshot – later changes don't sneak in
            pendingAt = savedAt;
        }
        schedule();
    }

    private static void schedule() {
        synchronized (LOCK) {
            if (!ready || scheduled || pendingData == null) {
                return;
            }
            scheduled = true;
        }
        TIMER.schedule(new Runnable() {
            public void run() {
                upload();
            }
        }, 5, TimeUnit.SECONDS);
    }

    private static void upload() {
        String data;
        long at;
        synchronized (LOCK) {
            scheduled = false;
            data = pendingData;
            at = pendingAt;
            pendingData = null;
        }
        if (data == null) {
            return;
        }
        try {
            JsonObject body = new JsonObject();
            body.addProperty("savedAt", at);
            body.addProperty("data", data);
            NexusHttp.request("PUT", "/api/me/mod-settings", body);
            status = "Synced";
        } catch (Exception e) {
            status = "Couldn't reach the Nexus server";
            synchronized (LOCK) {
                if (pendingData == null) { // nothing newer arrived – try this one again later
                    pendingData = data;
                    pendingAt = at;
                }
            }
            TIMER.schedule(new Runnable() {
                public void run() {
                    schedule();
                }
            }, 60, TimeUnit.SECONDS);
        }
    }
}
