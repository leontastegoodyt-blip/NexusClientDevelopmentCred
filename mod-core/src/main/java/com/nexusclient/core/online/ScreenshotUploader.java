package com.nexusclient.core.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.settings.BooleanSetting;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ScreenshotUploader – press F2 as usual; Nexus uploads the new screenshot to the Nexus server and
 * copies a link to it, ready to paste in Discord or chat ("Screenshot uploaded – link copied").
 *
 * How: once a second it looks in the instance's screenshots folder for a picture newer than the last
 * one it saw. A new one is sent (in the background, so the game never stutters) to
 * POST /api/screenshots with the player's Nexus sign-in (NEXUS_TOKEN from the launcher). The server
 * answers with the link (/s/<random id>.png), which is copied on the game thread.
 */
public class ScreenshotUploader extends Module {

    public final BooleanSetting copyLink = add(new BooleanSetting("copy", "Copy the link", true));

    private long newestSeen = -1;
    private int ticks;

    public ScreenshotUploader() {
        super("screenshotUploader", "Screenshot Uploader", "Uploads your screenshots (F2) and copies a link to share.",
                Category.UTILITY, false, false);
    }

    @Override
    protected void onEnable() {
        newestSeen = newestScreenshotTime(); // only upload screenshots taken from now on
    }

    @Override
    public void onTick(GameInfo game) {
        if (++ticks % 20 != 0) {
            return; // once a second is plenty
        }
        if (newestSeen < 0) {
            newestSeen = newestScreenshotTime();
            return;
        }
        File newest = newestScreenshot();
        if (newest == null || newest.lastModified() <= newestSeen) {
            return;
        }
        newestSeen = newest.lastModified();
        upload(newest);
    }

    private void upload(final File picture) {
        final String backend = System.getenv("NEXUS_BACKEND");
        final String token = System.getenv("NEXUS_TOKEN");
        if (backend == null || token == null || token.isEmpty()) {
            NexusCore.get().noticeLater("Screenshot Uploader needs you signed in to Nexus in the launcher.");
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Wait until Minecraft has finished writing the file.
                    Thread.sleep(400);
                    HttpURLConnection connection = (HttpURLConnection) new URL(backend + "/api/screenshots").openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(20000);
                    connection.setRequestProperty("Content-Type", "image/png");
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    InputStream file = new FileInputStream(picture);
                    OutputStream out = connection.getOutputStream();
                    try {
                        byte[] buffer = new byte[16384];
                        int read;
                        while ((read = file.read(buffer)) > 0) {
                            out.write(buffer, 0, read);
                        }
                    } finally {
                        file.close();
                        out.close();
                    }
                    if (connection.getResponseCode() != 200) {
                        NexusCore.get().noticeLater("Screenshot upload failed (" + connection.getResponseCode() + ").");
                        return;
                    }
                    InputStream in = connection.getInputStream();
                    final String link;
                    try {
                        JsonObject answer = new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonObject();
                        link = backend + answer.get("url").getAsString();
                    } finally {
                        in.close();
                    }
                    NexusCore.get().runLater(new Runnable() {
                        @Override
                        public void run() {
                            if (copyLink.isOn()) {
                                NexusCore.get().platform().copyToClipboard(link);
                                NexusCore.get().platform().showNotice("Screenshot uploaded – link copied: " + link);
                            } else {
                                NexusCore.get().platform().showNotice("Screenshot uploaded: " + link);
                            }
                        }
                    });
                } catch (Exception e) {
                    NexusCore.get().noticeLater("Screenshot upload failed – is the Nexus server online?");
                }
            }
        }, "Nexus screenshot upload");
        thread.setDaemon(true);
        thread.start();
    }

    private static File folder() {
        File gameDir = NexusCore.get().gameDir();
        return gameDir == null ? null : new File(gameDir, "screenshots");
    }

    private static File newestScreenshot() {
        File folder = folder();
        File[] files = folder == null ? null : folder.listFiles();
        File newest = null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".png") && (newest == null || file.lastModified() > newest.lastModified())) {
                    newest = file;
                }
            }
        }
        return newest;
    }

    private static long newestScreenshotTime() {
        File newest = newestScreenshot();
        return newest == null ? 0 : newest.lastModified();
    }
}
