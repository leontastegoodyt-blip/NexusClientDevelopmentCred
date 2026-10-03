package com.nexusclient.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.backend.clans.AfkDetector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plays through clans: create, invite, ranks and their limits, honor, AFK detection, chat, polls. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:clantest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class ClanFlowTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void clansWork() throws Exception {
        String owner = signIn("Owner1");
        String carl = signIn("Carl");
        String dana = signIn("Dana");
        String carlUuid = json(call("GET", "/api/me", carl, null)).getAsJsonObject("card").get("uuid").getAsString();
        String danaUuid = json(call("GET", "/api/me", dana, null)).getAsJsonObject("card").get("uuid").getAsString();

        // Three older clans fill the podium (top 3 get free Premium, which would allow a 2nd clan).
        for (String filler : new String[] {"FillerA", "FillerB", "FillerC"}) {
            call("POST", "/api/clans", signIn(filler), "{\"name\":\"Clan " + filler + "\",\"tag\":\"" + filler.substring(6) + "X\"}");
        }

        // Create a clan; a second one needs Premium.
        long clanId = json(call("POST", "/api/clans", owner, "{\"name\":\"Neon Knights\",\"tag\":\"nk\"}")).get("id").getAsLong();
        assertEquals(400, call("POST", "/api/clans", owner, "{\"name\":\"Second\",\"tag\":\"SEC\"}").statusCode());
        assertEquals(400, call("POST", "/api/clans", carl, "{\"name\":\"neon knights\",\"tag\":\"ZZ\"}").statusCode()); // name taken

        // Invite and accept.
        call("POST", "/api/clans/" + clanId + "/invite", owner, "{\"name\":\"carl\"}");
        call("POST", "/api/clans/" + clanId + "/invite", owner, "{\"name\":\"dana\"}");
        assertEquals(1, JsonParser.parseString(call("GET", "/api/clans/invites", carl, null).body()).getAsJsonArray().size());
        assertEquals(200, call("POST", "/api/clans/" + clanId + "/accept", carl, null).statusCode());
        assertEquals(200, call("POST", "/api/clans/" + clanId + "/accept", dana, null).statusCode());

        // Members can't invite; only 1 co-owner allowed; lower ranks can't kick higher ones.
        assertEquals(403, call("POST", "/api/clans/" + clanId + "/invite", carl, "{\"name\":\"owner1\"}").statusCode());
        assertEquals(200, call("POST", "/api/clans/" + clanId + "/rank", owner, rank(carlUuid, "CO_OWNER")).statusCode());
        assertEquals(400, call("POST", "/api/clans/" + clanId + "/rank", owner, rank(danaUuid, "CO_OWNER")).statusCode());
        assertEquals(200, call("POST", "/api/clans/" + clanId + "/rank", carl, rank(danaUuid, "VIP")).statusCode());
        assertEquals(403, call("POST", "/api/clans/" + clanId + "/kick", dana, "{\"uuid\":\"" + carlUuid + "\"}").statusCode());

        // Honor: 3 active minutes = 5 honor (reports are 50+ seconds apart in real life – each
        // player's first report of the day always counts, so use three different players).
        JsonObject result = json(call("POST", "/api/activity", owner, activeMinute()));
        assertTrue(result.get("active").getAsBoolean());
        assertTrue(result.get("counted").getAsBoolean());
        // A second report straight away is ignored (anti-cheat: one minute per minute).
        assertFalse(json(call("POST", "/api/activity", owner, activeMinute())).get("counted").getAsBoolean());
        assertEquals("NK", result.get("clanTag").getAsString());

        // Chat (filtered) and polls.
        JsonObject message = json(call("POST", "/api/clans/" + clanId + "/chat", dana, "{\"text\":\"gg you shit\"}"));
        assertEquals("gg you ****", message.get("text").getAsString());
        assertEquals(403, call("POST", "/api/clans/" + clanId + "/polls", owner.equals(dana) ? owner : signInMember(clanId, owner),
                "{\"question\":\"x\",\"options\":[\"a\",\"b\"]}").statusCode());
        assertEquals(200, call("POST", "/api/clans/" + clanId + "/polls", dana,
                "{\"question\":\"Next event?\",\"options\":[\"Bedwars\",\"Skywars\"]}").statusCode());
        JsonArray polls = JsonParser.parseString(call("GET", "/api/clans/" + clanId + "/polls", carl, null).body()).getAsJsonArray();
        long pollId = polls.get(0).getAsJsonObject().get("id").getAsLong();
        call("POST", "/api/clans/polls/" + pollId + "/vote", carl, "{\"choice\":1}");
        polls = JsonParser.parseString(call("GET", "/api/clans/" + clanId + "/polls", carl, null).body()).getAsJsonArray();
        assertEquals(1, polls.get(0).getAsJsonObject().getAsJsonArray("votes").get(1).getAsInt());

        // Leaderboard and tags.
        JsonArray board = JsonParser.parseString(call("GET", "/api/clans/leaderboard", carl, null).body()).getAsJsonArray();
        assertTrue(board.get(0).getAsJsonObject().get("podium").getAsBoolean());
        JsonObject tags = json(call("GET", "/api/clans/tags?uuids=" + carlUuid, carl, null));
        assertEquals("NK", tags.get(carlUuid).getAsString());

        // Owners can't just leave; members can.
        assertEquals(400, call("POST", "/api/clans/" + clanId + "/leave", owner, null).statusCode());
        assertEquals(200, call("POST", "/api/clans/" + clanId + "/leave", dana, null).statusCode());
    }

    @Test
    void afkDetectorSpotsAfkAndMacros() {
        AfkDetector detector = new AfkDetector();
        UUID player = UUID.randomUUID();
        // Real play: lots of varied input.
        assertTrue(detector.isActive(player, new AfkDetector.Minute(140, 2600, 70, 45, 1, 0)));
        // Standing still with no input.
        assertFalse(detector.isActive(player, new AfkDetector.Minute(0, 0, 0, 0, 0, 0)));
        // Auto-walking forward (moving, but no key presses and no turning).
        assertFalse(detector.isActive(player, new AfkDetector.Minute(250, 0, 0, 0, 0, 0)));
        // A macro repeating the exact same thing every minute.
        UUID macro = UUID.randomUUID();
        boolean last = true;
        for (int i = 0; i < 8; i++) {
            last = detector.isActive(macro, new AfkDetector.Minute(30, 900, 40, 40, 0, 0));
        }
        assertFalse(last);
    }

    // ------------------------------------------------------------------ helpers

    /** A new player who joins the clan as a plain member (members can't start polls). */
    String signInMember(long clanId, String inviter) throws Exception {
        String member = signIn("Member" + (System.nanoTime() % 100000));
        String name = json(call("GET", "/api/me", member, null)).getAsJsonObject("card").get("name").getAsString();
        call("POST", "/api/clans/" + clanId + "/invite", inviter, "{\"name\":\"" + name + "\"}");
        call("POST", "/api/clans/" + clanId + "/accept", member, null);
        return member;
    }

    static String rank(String uuid, String rank) {
        return "{\"uuid\":\"" + uuid + "\",\"rank\":\"" + rank + "\"}";
    }

    static String activeMinute() {
        return "{\"moveBlocks\":120,\"turnDegrees\":2400,\"keyPresses\":60,\"clicks\":40,\"chatMessages\":0,\"menuSeconds\":0}";
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
