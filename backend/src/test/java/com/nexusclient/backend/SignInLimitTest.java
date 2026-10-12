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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sign-in is rate limited: after 30 attempts in 10 minutes from one address, the next is refused (429). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:limittest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class SignInLimitTest {

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void signInIsLimited() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertEquals(200, call("POST", "/api/auth/challenge", null, null).statusCode());
        }
        HttpResponse<String> refused = call("POST", "/api/auth/challenge", null, null);
        assertEquals(429, refused.statusCode());
        assertTrue(refused.body().contains("Too many sign-in attempts"), refused.body());
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
