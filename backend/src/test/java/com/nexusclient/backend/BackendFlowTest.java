package com.nexusclient.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real backend (with an in-memory database) and plays through the main features as two
 * test players, Alice and Bob: sign-in, friends, live notifications, DMs (with the chat filter),
 * parties, share codes, blocking and reports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class BackendFlowTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();
    final Map<String, WebSocket> sockets = new HashMap<>();

    @Test
    void mainFeaturesWork() throws Exception {
        // Nothing works without signing in.
        assertEquals(401, call("GET", "/api/friends", null, null).statusCode());

        String alice = signIn("Alice");
        String bob = signIn("Bob");
        String bobUuid = json(call("GET", "/api/me", bob, null)).getAsJsonObject("card").get("uuid").getAsString();
        String aliceUuid = json(call("GET", "/api/me", alice, null)).getAsJsonObject("card").get("uuid").getAsString();

        // Both connect live; Alice sends a friend request; Bob hears about it straight away.
        BlockingQueue<JsonObject> bobEvents = connect(bob);
        BlockingQueue<JsonObject> aliceEvents = connect(alice);
        assertEquals("sent", json(call("POST", "/api/friends/request", alice, "{\"name\":\"bob\"}")).get("result").getAsString());
        next(bobEvents, "friend.request");

        // DMs are friends-only: not allowed yet.
        assertEquals(403, call("POST", "/api/messages/" + bobUuid, alice, "{\"text\":\"hi\"}").statusCode());

        call("POST", "/api/friends/accept", bob, "{\"uuid\":\"" + aliceUuid + "\"}");
        JsonObject friends = json(call("GET", "/api/friends", alice, null));
        JsonObject firstFriend = friends.getAsJsonArray("friends").get(0).getAsJsonObject();
        assertEquals("Bob", firstFriend.get("name").getAsString());
        assertTrue(firstFriend.get("online").getAsBoolean());

        // Bob joins a server: Alice sees where he is.
        sockets.get(bob).sendText("{\"type\":\"presence\",\"server\":\"mc.hypixel.net\"}", true);
        JsonObject presence = next(aliceEvents, "friend.presence");
        assertEquals("mc.hypixel.net", presence.getAsJsonObject("friend").get("server").getAsString());

        // A DM with a bad word: filtered, and Bob gets it live.
        call("POST", "/api/messages/" + bobUuid, alice, "{\"text\":\"what the SHIIIT\"}");
        JsonObject dm = next(bobEvents, "dm");
        assertEquals("what the ******", dm.getAsJsonObject("message").get("text").getAsString());

        // Party: invite, accept, chat, warp.
        call("POST", "/api/party/invite", alice, "{\"uuid\":\"" + bobUuid + "\"}");
        String partyId = next(bobEvents, "party.invite").get("partyId").getAsString();
        assertEquals(200, call("POST", "/api/party/accept", bob, "{\"partyId\":\"" + partyId + "\"}").statusCode());
        call("POST", "/api/party/chat", bob, "{\"text\":\"gg\"}");
        assertEquals("gg", next(aliceEvents, "party.chat").get("text").getAsString());
        call("POST", "/api/party/warp", alice, "{\"server\":\"mc.hypixel.net\"}");
        assertEquals("mc.hypixel.net", next(bobEvents, "party.warp").get("server").getAsString());

        // Share codes.
        String code = json(call("POST", "/api/share", alice, "{\"kind\":\"HUD\",\"data\":\"NXH-abc\"}")).get("code").getAsString();
        assertTrue(code.matches("NX-[A-Z0-9]{4}-[A-Z0-9]{4}"), code);
        assertEquals("NXH-abc", json(call("GET", "/api/share/" + code.toLowerCase(), bob, null)).get("data").getAsString());

        // Blocking ends the friendship and stops DMs.
        call("POST", "/api/friends/block", bob, "{\"uuid\":\"" + aliceUuid + "\"}");
        assertEquals(0, json(call("GET", "/api/friends", alice, null)).getAsJsonArray("friends").size());
        assertEquals(403, call("POST", "/api/messages/" + bobUuid, alice, "{\"text\":\"hi\"}").statusCode());

        // Reports: anyone can report; only staff can read them.
        assertEquals(200, call("POST", "/api/reports", bob, "{\"target\":\"" + aliceUuid + "\",\"reason\":\"CHAT\"}").statusCode());
        assertEquals(403, call("GET", "/api/admin/reports", bob, null).statusCode());
    }

    // ------------------------------------------------------------------ helpers

    String signIn(String name) throws Exception {
        HttpResponse<String> response = call("POST", "/api/auth/dev", null, "{\"name\":\"" + name + "\"}");
        return json(response).get("token").getAsString();
    }

    HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonObject json(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    BlockingQueue<JsonObject> connect(String token) throws Exception {
        BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
        WebSocket socket = http.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocket.Listener() {
                    final StringBuilder text = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            events.add(JsonParser.parseString(text.toString()).getAsJsonObject());
                            text.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
        sockets.put(token, socket);
        return events;
    }

    /** Waits (up to 5 seconds) for the next event of this type, skipping others. */
    static JsonObject next(BlockingQueue<JsonObject> events, String type) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            JsonObject event = events.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (event != null && type.equals(event.get("type").getAsString())) {
                return event;
            }
        }
        throw new AssertionError("No " + type + " event arrived");
    }
}
