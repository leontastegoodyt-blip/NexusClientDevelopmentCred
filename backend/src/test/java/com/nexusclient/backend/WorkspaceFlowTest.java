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
 * Mod Rerender: a workspace is only visible while shared, can be found by name (any capitals) or
 * UUID, junk is refused, and stopping sharing removes it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:workspacetest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class WorkspaceFlowTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void workspaces() throws Exception {
        String alice = signIn("WorkAlice");
        String bob = signIn("WorkBob");
        assertEquals(404, call("GET", "/api/workspace/WorkAlice", bob, null).statusCode()); // not shared yet
        assertEquals(404, call("GET", "/api/workspace/NobodyAtAll", bob, null).statusCode());

        JsonObject share = new JsonObject();
        share.addProperty("data", "{\"format\":1,\"content\":[]}");
        json(call("PUT", "/api/workspace", alice, share.toString()));
        JsonObject seen = json(call("GET", "/api/workspace/workalice", bob, null));
        assertEquals("WorkAlice", seen.get("name").getAsString());
        assertEquals("{\"format\":1,\"content\":[]}", seen.get("data").getAsString());
        assertEquals(200, call("GET", "/api/workspace/" + seen.get("uuid").getAsString(), bob, null).statusCode());

        JsonObject junk = new JsonObject();
        junk.addProperty("data", "not json");
        assertEquals(400, call("PUT", "/api/workspace", alice, junk.toString()).statusCode());
        assertEquals(401, call("GET", "/api/workspace/WorkAlice", null, null).statusCode());

        json(call("DELETE", "/api/workspace", alice, null));
        assertEquals(404, call("GET", "/api/workspace/WorkAlice", bob, null).statusCode());
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
