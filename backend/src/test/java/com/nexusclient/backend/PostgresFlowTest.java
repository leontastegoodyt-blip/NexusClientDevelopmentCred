package com.nexusclient.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The online server uses PostgreSQL, while every other test uses the small H2 development database.
 * This test starts a REAL PostgreSQL (downloaded and run by the "embedded-postgres" library) and plays
 * through the main features, so database differences show up here instead of on the live server:
 * every table is created by the Flyway files, and sign-in, friends, clans, honor, badges, cosmetics,
 * partners, crash reports and the developer tools all work.
 * "DevTest" is the developer below (its offline UUID).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "nexus.developers=025b3794-a8c2-3489-a7f1-f066312727c3")
class PostgresFlowTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't start PostgreSQL for the test", e);
        }
    }

    /** Point the server at the PostgreSQL started above (instead of the H2 file). */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void everythingWorksOnPostgres() throws Exception {
        String dev = signIn("DevTest");
        String alice = signIn("Alice");
        String aliceUuid = json(call("GET", "/api/me", alice, null)).getAsJsonObject("card").get("uuid").getAsString();
        String devUuid = json(call("GET", "/api/me", dev, null)).getAsJsonObject("card").get("uuid").getAsString();

        // Friends.
        assertEquals(200, call("POST", "/api/friends/request", dev, "{\"name\":\"Alice\"}").statusCode());
        assertEquals(200, call("POST", "/api/friends/accept", alice, "{\"uuid\":\"" + devUuid + "\"}").statusCode());
        assertEquals(1, json(call("GET", "/api/friends", dev, null)).getAsJsonArray("friends").size());

        // Clans, honor from play, the developer's honor tool, the leaderboard.
        long clanId = json(call("POST", "/api/clans", dev, "{\"name\":\"ApplesCo\",\"tag\":\"APPLE\"}")).get("id").getAsLong();
        String minute = "{\"moveBlocks\":40,\"turnDegrees\":300,\"keyPresses\":60,\"clicks\":20,\"chatMessages\":0,\"menuSeconds\":0}";
        assertEquals(200, call("POST", "/api/activity", dev, minute).statusCode());
        assertEquals(200, call("POST", "/api/admin/clans/" + clanId + "/honor", dev, "{\"amount\":100000}").statusCode());
        assertEquals(403, call("POST", "/api/admin/clans/" + clanId + "/honor", alice, "{\"amount\":5}").statusCode());
        JsonArray board = JsonParser.parseString(call("GET", "/api/clans/leaderboard", dev, null).body()).getAsJsonArray();
        assertTrue(board.get(0).getAsJsonObject().get("honor").getAsLong() >= 100000);

        // Badges, cosmetics, partners, crash reports (these use hand-written SQL).
        assertEquals("developer", json(call("GET", "/api/badges?uuids=" + devUuid, null, null)).get(devUuid).getAsString());
        assertEquals(200, call("POST", "/api/cosmetics/equip", alice, "{\"type\":\"CAPE\",\"id\":\"nexus_cape\"}").statusCode());
        assertTrue(json(call("GET", "/api/cosmetics/worn?uuids=" + aliceUuid, null, null)).has(aliceUuid));
        assertEquals(200, call("POST", "/api/admin/partners", dev, "{\"name\":\"Test\",\"address\":\"play.test.net\","
                + "\"description\":\"x\",\"manager\":\"" + aliceUuid + "\"}").statusCode());
        assertEquals(200, call("POST", "/api/crash-reports", null, "{\"version\":\"0.1.0\",\"report\":\"boom\"}").statusCode());
        assertEquals(1, JsonParser.parseString(call("GET", "/api/admin/crash-reports", dev, null).body()).getAsJsonArray().size());

        // Developer tools: search, overview, timed ban.
        assertEquals(1, JsonParser.parseString(call("GET", "/api/admin/players?search=ali", dev, null).body()).getAsJsonArray().size());
        assertEquals(2, json(call("GET", "/api/admin/overview", dev, null)).get("players").getAsLong());
        assertEquals(200, call("POST", "/api/admin/players/" + aliceUuid + "/ban", dev, "{\"hours\":1,\"reason\":\"test\"}").statusCode());
        assertEquals(401, call("GET", "/api/me", alice, null).statusCode());
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
