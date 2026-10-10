package com.nexusclient.launcher.online;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.launcher.DevMode;
import com.nexusclient.launcher.account.Account;
import com.nexusclient.launcher.account.AccountManager;
import javafx.application.Platform;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * NexusOnline – the launcher's connection to the Nexus online server (the backend module):
 * signing in, the web API (/api/…), and the live connection (/ws) that brings friend requests,
 * DMs, party invites and "friend came online" as they happen.
 *
 * Signing in never sends the Microsoft login or password anywhere except Microsoft/Mojang:
 *  1. ask Nexus for a code, 2. tell Mojang "I'm joining <code>" with the Minecraft token,
 *  3. Nexus asks Mojang to confirm. In developer mode without an account, the test sign-in is used.
 *
 * The server address is -Dnexus.backend (default http://localhost:8080 until Nexus is hosted).
 * All methods that talk to the network block – call them from Background.run, never the UI thread.
 */
public final class NexusOnline {

    public enum State { SIGNED_OUT, CONNECTING, ONLINE, OFFLINE }

    private static final NexusOnline INSTANCE = new NexusOnline();
    private static final Gson GSON = new Gson();

    private final String base = System.getProperty("nexus.backend", "http://localhost:8080");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final List<Consumer<JsonObject>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<State>> stateListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Nexus online");
        thread.setDaemon(true);
        return thread;
    });

    private volatile String token;
    private volatile String myUuid;
    private volatile String myName;
    private volatile String role = "PLAYER";
    private volatile WebSocket socket;
    private volatile State state = State.SIGNED_OUT;

    private NexusOnline() {
        // Keep the live connection open: a small "ping" every 30 seconds.
        timer.scheduleAtFixedRate(() -> {
            WebSocket ws = socket;
            if (ws != null) {
                ws.sendText("{\"type\":\"ping\"}", true);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public static NexusOnline get() {
        return INSTANCE;
    }

    public State state() { return state; }
    public String myUuid() { return myUuid; }
    public String myName() { return myName; }
    /** True when the signed-in account is the Nexus developer (the owner). */
    public boolean isDeveloper() { return "DEVELOPER".equals(role); }
    /** True for moderators, admins and the developer. */
    public boolean isStaff() { return !"PLAYER".equals(role); }
    public boolean signedIn() { return token != null; }

    /** Hears every live event (called on the UI thread). */
    public void addListener(Consumer<JsonObject> listener) {
        listeners.add(listener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    // ------------------------------------------------------------------ signing in

    /** Signs in with the active Minecraft account (or the dev test player) and connects live. */
    public synchronized void signIn(AccountManager accounts) throws IOException, InterruptedException {
        if (token != null) {
            return;
        }
        setState(State.CONNECTING);
        try {
            LocalServer.ensureRunning(base); // starts the Nexus server on this PC if needed (until it's hosted online)
            AccountManager.SavedAccount active = accounts.getActive();
            JsonObject signedIn;
            if (active != null) {
                Account account = accounts.accountForLaunch(active);
                String serverId = send("POST", "/api/auth/challenge", null).getAsJsonObject().get("serverId").getAsString();
                joinAtMojang(account, serverId);
                signedIn = send("POST", "/api/auth/verify",
                        Map.of("username", account.username(), "serverId", serverId)).getAsJsonObject();
            } else if (DevMode.enabled()) {
                signedIn = send("POST", "/api/auth/dev", Map.of("name", "NexusDev")).getAsJsonObject();
            } else {
                throw new IOException("Sign in with a Microsoft account to use friends and parties.");
            }
            token = signedIn.get("token").getAsString();
            myUuid = signedIn.get("uuid").getAsString();
            myName = signedIn.get("name").getAsString();
            // Our role on the Nexus server (PLAYER, MODERATOR, ADMIN or DEVELOPER). The server checks it
            // again for every admin action – this copy is only used to show or hide the Developer tab.
            role = get("/api/me").getAsJsonObject().get("role").getAsString();
            connectLive();
        } catch (IOException | InterruptedException | RuntimeException e) {
            setState(State.OFFLINE);
            throw e;
        } catch (Exception e) { // e.g. LoginException from the account
            setState(State.OFFLINE);
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Step 2 of signing in: tell Mojang we're "joining" Nexus (proves we own the account). */
    private void joinAtMojang(Account account, String serverId) throws IOException, InterruptedException {
        String body = GSON.toJson(Map.of("accessToken", account.accessToken(),
                "selectedProfile", account.uuid().replace("-", ""), "serverId", serverId));
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw new IOException("Mojang didn't accept the sign-in (" + response.statusCode() + ")");
        }
    }

    private void connectLive() {
        http.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                .buildAsync(URI.create(base.replaceFirst("^http", "ws") + "/ws"), new WebSocket.Listener() {
                    private final StringBuilder text = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket ws) {
                        socket = ws;
                        setState(State.ONLINE);
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            try {
                                JsonObject event = JsonParser.parseString(text.toString()).getAsJsonObject();
                                Platform.runLater(() -> listeners.forEach(listener -> listener.accept(event)));
                            } catch (RuntimeException ignored) {
                                // not JSON – ignore
                            }
                            text.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                        lostConnection();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        lostConnection();
                    }
                }).exceptionally(error -> {
                    lostConnection();
                    return null;
                });
    }

    /** The live connection dropped: try again in 15 seconds (with the same sign-in). */
    private void lostConnection() {
        socket = null;
        if (token == null) {
            return;
        }
        setState(State.OFFLINE);
        timer.schedule(() -> {
            if (socket == null && token != null) {
                connectLive();
            }
        }, 15, TimeUnit.SECONDS);
    }

    /** Tells friends which server we're on ("" = not on a server). */
    public void setPresence(String server) {
        WebSocket ws = socket;
        if (ws != null) {
            ws.sendText(GSON.toJson(Map.of("type", "presence", "server", server == null ? "" : server)), true);
        }
    }

    private void setState(State newState) {
        state = newState;
        Platform.runLater(() -> stateListeners.forEach(listener -> listener.accept(newState)));
    }

    // ------------------------------------------------------------------ the web API

    public JsonElement get(String path) throws IOException, InterruptedException {
        return send("GET", path, null);
    }

    public JsonElement put(String path, Object body) throws IOException, InterruptedException {
        return send("PUT", path, body == null ? Map.of() : body);
    }

    public JsonElement delete(String path) throws IOException, InterruptedException {
        return send("DELETE", path, null);
    }

    /** The sign-in token, for handing to the game (so the in-game mod can report activity for honor). */
    public String token() {
        return token;
    }

    /** The Nexus server address. */
    public String baseUrl() {
        return base;
    }

    public JsonElement post(String path, Object body) throws IOException, InterruptedException {
        return send("POST", path, body == null ? Map.of() : body);
    }

    /** Sends a request; on an error, throws an IOException with the server's plain-English reason. */
    private JsonElement send(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("Can't reach the Nexus server (" + base + ")");
        }
        JsonElement json = response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body());
        if (response.statusCode() == 401 && token != null) {
            token = null; // signed out (expired or banned) – sign in again next time
            role = "PLAYER";
            setState(State.SIGNED_OUT);
        }
        if (response.statusCode() >= 400) {
            String reason = json.isJsonObject() && json.getAsJsonObject().has("error")
                    ? json.getAsJsonObject().get("error").getAsString() : "Nexus server error " + response.statusCode();
            throw new IOException(reason);
        }
        return json;
    }
}
