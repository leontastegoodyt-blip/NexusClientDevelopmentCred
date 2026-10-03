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
 * Partner Servers and Premium: an admin adds a partner with a manager; the partner list is public;
 * only the manager (or an admin) can change its Nexus rules; an admin can give Premium.
 * "AdminTest" is made an admin below (its offline UUID).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:partnertest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "nexus.admins=613e92ee-d58b-34f0-bc88-35d8e3124a9e"})
class PartnerFlowTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void partnersAndPremiumWork() throws Exception {
        String admin = signIn("AdminTest");
        String manager = signIn("ServerManager");
        String stranger = signIn("Stranger");
        String managerUuid = json(call("GET", "/api/me", manager, null)).getAsJsonObject("card").get("uuid").getAsString();
        String strangerUuid = json(call("GET", "/api/me", stranger, null)).getAsJsonObject("card").get("uuid").getAsString();
        assertEquals("ADMIN", json(call("GET", "/api/me", admin, null)).get("role").getAsString());

        // Only admins add partners.
        String partner = "{\"name\":\"Nexus Test\",\"address\":\"play.nexustest.net\",\"description\":\"PvP\",\"manager\":\"" + managerUuid + "\"}";
        assertEquals(403, call("POST", "/api/admin/partners", stranger, partner).statusCode());
        long id = json(call("POST", "/api/admin/partners", admin, partner)).get("id").getAsLong();

        // The list is public (no sign-in).
        JsonArray list = JsonParser.parseString(call("GET", "/api/partners", null, null).body()).getAsJsonArray();
        assertEquals("play.nexustest.net", list.get(0).getAsJsonObject().get("address").getAsString());

        // Only the manager can set the rules.
        String rules = "{\"blocked\":[\"minimap\",\"*blockable\"]}";
        assertEquals(403, call("PUT", "/api/partners/" + id + "/rules", stranger, rules).statusCode());
        JsonObject updated = json(call("PUT", "/api/partners/" + id + "/rules", manager, rules));
        assertEquals(2, updated.getAsJsonArray("blockedMods").size());
        assertTrue(updated.get("youManage").getAsBoolean());

        // Premium: not yet, then given by an admin.
        assertFalse(json(call("GET", "/api/me", stranger, null)).get("premium").getAsBoolean());
        assertEquals(200, call("POST", "/api/admin/players/" + strangerUuid + "/premium", admin, "{\"days\":30}").statusCode());
        JsonObject me = json(call("GET", "/api/me", stranger, null));
        assertTrue(me.get("premium").getAsBoolean());
        assertFalse(me.get("premiumFromPodium").getAsBoolean());

        // Cosmetics: wear a cape; anyone's game can ask what they wear; the picture is served.
        JsonArray catalogue = JsonParser.parseString(call("GET", "/api/cosmetics", stranger, null).body()).getAsJsonArray();
        assertEquals(6, catalogue.size());
        assertEquals(200, call("POST", "/api/cosmetics/equip", stranger, "{\"type\":\"CAPE\",\"id\":\"nexus_cape\"}").statusCode());
        assertEquals(404, call("POST", "/api/cosmetics/equip", stranger, "{\"type\":\"CAPE\",\"id\":\"halo\"}").statusCode());
        JsonObject worn = json(call("GET", "/api/cosmetics/worn?uuids=" + strangerUuid + "," + managerUuid, null, null));
        assertEquals("nexus_cape", worn.getAsJsonObject(strangerUuid).get("CAPE").getAsString());
        assertFalse(worn.has(managerUuid));
        assertEquals(200, call("GET", "/cosmetics/nexus_cape.png", null, null).statusCode());
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
