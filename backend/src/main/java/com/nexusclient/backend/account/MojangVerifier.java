package com.nexusclient.backend.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * MojangVerifier – checks that a player really owns their Minecraft account, WITHOUT Nexus ever
 * seeing their password or Microsoft login. It's the same check every Minecraft server does:
 *
 *  1. Nexus gives the launcher a random code (the "server id").
 *  2. The launcher tells Mojang "I'm joining server <code>", using the player's own Minecraft token.
 *  3. Nexus asks Mojang "has <name> joined server <code>?" – only the real owner could have done step 2.
 */
@Component
public class MojangVerifier {

    /** The player Mojang confirmed. */
    public record Profile(UUID uuid, String name) {}

    private final RestClient mojang = RestClient.create("https://sessionserver.mojang.com");

    public Optional<Profile> hasJoined(String username, String serverId) {
        String body = mojang.get()
                .uri(uri -> uri.path("/session/minecraft/hasJoined")
                        .queryParam("username", username)
                        .queryParam("serverId", serverId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { }) // treated as "no" below
                .body(String.class);
        if (body == null || body.isBlank()) {
            return Optional.empty(); // Mojang answers "204 No Content" when they didn't join
        }
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("id") || !json.has("name")) {
            return Optional.empty();
        }
        return Optional.of(new Profile(fromMojangId(json.get("id").getAsString()), json.get("name").getAsString()));
    }

    /** Mojang writes UUIDs without dashes ("069a79f4…"); Java wants them with dashes. */
    static UUID fromMojangId(String id) {
        return UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }
}
