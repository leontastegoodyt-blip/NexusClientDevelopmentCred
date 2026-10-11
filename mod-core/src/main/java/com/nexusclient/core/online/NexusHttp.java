package com.nexusclient.core.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * NexusHttp – talks to the Nexus server's web API from the game (Java 8, so it works on 1.8.9 too).
 *
 * The launcher tells the game where the server is (NEXUS_BACKEND) and passes the player's sign-in
 * (NEXUS_TOKEN). Every call here is slow-ish (it goes over the internet), so callers run them in the
 * background with {@link #async(Runnable)} – never on the game thread, or the game would freeze.
 */
public final class NexusHttp {

    public static final String BACKEND = System.getenv("NEXUS_BACKEND");
    public static final String TOKEN = System.getenv("NEXUS_TOKEN");

    /** One background worker for all the requests, so they run in order and never pile up threads. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Nexus online");
            thread.setDaemon(true);
            return thread;
        }
    });

    private NexusHttp() {}

    /** True if the launcher connected this game to the Nexus server and signed the player in. */
    public static boolean signedIn() {
        return BACKEND != null && TOKEN != null && !TOKEN.isEmpty();
    }

    /** Runs a task in the background (requests go here). */
    public static void async(Runnable task) {
        WORKER.execute(task);
    }

    public static JsonElement get(String path) throws IOException {
        return request("GET", path, null);
    }

    public static JsonElement post(String path, JsonObject body) throws IOException {
        return request("POST", path, body == null ? new JsonObject() : body);
    }

    /**
     * Sends one request and returns the JSON answer. A problem the server explains
     * ({"error": "…"}) becomes an IOException with that plain-English message.
     */
    public static JsonElement request(String method, String path, JsonObject body) throws IOException {
        if (BACKEND == null) {
            throw new IOException("Not connected to the Nexus server.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(BACKEND + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        if (TOKEN != null) {
            connection.setRequestProperty("Authorization", "Bearer " + TOKEN);
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            OutputStream out = connection.getOutputStream();
            try {
                out.write(body.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
        }
        int status = connection.getResponseCode();
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = in == null ? "" : readAll(in);
        if (status >= 400) {
            String reason = "The Nexus server said no (" + status + ").";
            try {
                JsonObject error = new JsonParser().parse(text).getAsJsonObject();
                if (error.has("error")) {
                    reason = error.get("error").getAsString();
                }
            } catch (RuntimeException ignored) {
                // not JSON – keep the general message
            }
            throw new IOException(reason);
        }
        return text.isEmpty() ? new JsonObject() : new JsonParser().parse(text);
    }

    static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
