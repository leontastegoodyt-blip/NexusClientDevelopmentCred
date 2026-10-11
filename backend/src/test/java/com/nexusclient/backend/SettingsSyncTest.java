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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Settings sync (the in-game settings follow the player between PCs): empty at first, saved, an older
 * copy never replaces a newer one, junk is refused, and each player only sees their own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:synctest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class SettingsSyncTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void settingsSync() throws Exception {
        String alice = signIn("SyncAlice");
        String bob = signIn("SyncBob");

        assertEquals(0, json(call("GET", "/api/me/mod-settings", alice, null)).get("savedAt").getAsLong());

        // PC 1 saves.
        JsonObject saved = json(call("PUT", "/api/me/mod-settings", alice, body(1000, "{\"modules\":{\"fps\":{}}}")));
        assertEquals(1000, saved.get("savedAt").getAsLong());

        // An older copy (e.g. a PC that was offline) doesn't replace it – the newer one comes back.
        JsonObject older = json(call("PUT", "/api/me/mod-settings", alice, body(900, "{\"old\":true}")));
        assertEquals(1000, older.get("savedAt").getAsLong());
        assertEquals("{\"modules\":{\"fps\":{}}}", older.get("data").getAsString());

        // A newer copy does.
        json(call("PUT", "/api/me/mod-settings", alice, body(2000, "{\"modules\":{}}")));
        assertEquals("{\"modules\":{}}", json(call("GET", "/api/me/mod-settings", alice, null)).get("data").getAsString());

        // Junk and missing sign-in are refused; other players don't see Alice's settings.
        assertEquals(400, call("PUT", "/api/me/mod-settings", alice, body(3000, "not json")).statusCode());
        assertEquals(401, call("GET", "/api/me/mod-settings", null, null).statusCode());
        assertEquals(0, json(call("GET", "/api/me/mod-settings", bob, null)).get("savedAt").getAsLong());
    }

    private static String body(long savedAt, String data) {
        JsonObject body = new JsonObject();
        body.addProperty("savedAt", savedAt);
        body.addProperty("data", data);
        return body.toString();
    }

    private String signIn(String name) throws Exception {
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
