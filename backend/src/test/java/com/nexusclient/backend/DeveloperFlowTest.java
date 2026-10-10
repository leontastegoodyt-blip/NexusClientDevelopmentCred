package com.nexusclient.backend;

import com.google.gson.JsonArray;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The developer role (the Nexus owner): it shows in /api/me, it can search players, kick them and
 * ban them for a time – and nobody can kick or ban the developer, not even an admin.
 * "DevTest" is the developer and "AdminTest" an admin below (their offline UUIDs).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:devtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "nexus.developers=025b3794-a8c2-3489-a7f1-f066312727c3",
        "nexus.admins=613e92ee-d58b-34f0-bc88-35d8e3124a9e"})
class DeveloperFlowTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void developerCanKickAndBanButNobodyCanBanTheDeveloper() throws Exception {
        String dev = signIn("DevTest");
        String admin = signIn("AdminTest");
        String player = signIn("Troublemaker");
        String devUuid = uuidOf(dev);
        String playerUuid = uuidOf(player);
        assertEquals("DEVELOPER", json(call("GET", "/api/me", dev, null)).get("role").getAsString());

        // Normal players can't use any of it.
        assertEquals(403, call("GET", "/api/admin/overview", player, null).statusCode());
        assertEquals(403, call("POST", "/api/admin/players/" + devUuid + "/kick", player, "{}").statusCode());

        // The developer finds the player and sees the numbers.
        JsonArray found = JsonParser.parseString(call("GET", "/api/admin/players?search=trouble", dev, null).body()).getAsJsonArray();
        assertEquals(1, found.size());
        assertEquals("Troublemaker", found.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(3, json(call("GET", "/api/admin/overview", dev, null)).get("players").getAsLong());

        // Kick: their sign-in stops working at once, but they can sign in again.
        assertEquals(200, call("POST", "/api/admin/players/" + playerUuid + "/kick", dev, "{\"reason\":\"calm down\"}").statusCode());
        assertEquals(401, call("GET", "/api/me", player, null).statusCode());
        player = signIn("Troublemaker");

        // Ban for a day: signed out, and signing in is refused with the reason.
        assertEquals(200, call("POST", "/api/admin/players/" + playerUuid + "/ban", dev,
                "{\"hours\":24,\"reason\":\"spam\"}").statusCode());
        assertEquals(401, call("GET", "/api/me", player, null).statusCode());
        HttpResponse<String> refused = call("POST", "/api/auth/dev", null, "{\"name\":\"Troublemaker\"}");
        assertEquals(403, refused.statusCode());
        assertTrue(refused.body().contains("spam"));
        assertEquals(1, json(call("GET", "/api/admin/overview", dev, null)).get("banned").getAsLong());

        // Unban: they can sign in again.
        assertEquals(200, call("POST", "/api/admin/players/" + playerUuid + "/ban", dev, "{\"hours\":0}").statusCode());
        signIn("Troublemaker");

        // Nobody can kick or ban the developer – not even an admin – and the developer can't ban themselves.
        assertEquals(403, call("POST", "/api/admin/players/" + devUuid + "/ban", admin, "{\"hours\":24}").statusCode());
        assertEquals(403, call("POST", "/api/admin/players/" + devUuid + "/kick", admin, "{}").statusCode());
        assertEquals(400, call("POST", "/api/admin/players/" + devUuid + "/ban", dev, "{\"hours\":24}").statusCode());
        assertFalse(json(call("GET", "/api/me", dev, null)).get("card").isJsonNull());
    }

    String uuidOf(String token) throws Exception {
        return json(call("GET", "/api/me", token, null)).getAsJsonObject("card").get("uuid").getAsString();
    }

    String signIn(String name) throws Exception {
        return json(call("POST", "/api/auth/dev", null, "{\"name\":\"" + name + "\"}")).get("token").getAsString();
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
}
