package com.nexusclient.launcher.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.launcher.game.NexusPaths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * SkinService – changes the player's real Minecraft skin and cape through Mojang's official
 * "Minecraft Services" API, and keeps a library of saved skins on this computer.
 *
 * Changing the skin in Minecraft needs a signed-in account (a fresh Minecraft token from
 * AccountManager). The library works without signing in.
 */
public final class SkinService {

    private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    /** One cape the player owns. */
    public record Cape(String id, String alias, String url, boolean active) {}

    /** The player's current skin and capes. */
    public record Profile(String skinUrl, boolean slim, List<Cape> capes) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private SkinService() {}

    // ================================================================== the player's real skin (needs sign-in)

    /** Reads the player's current skin and capes. */
    public static Profile profile(Account account) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(PROFILE))
                .header("Authorization", "Bearer " + account.accessToken()).GET().build());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String skinUrl = null;
        boolean slim = false;
        for (JsonElement skin : json.has("skins") ? json.getAsJsonArray("skins") : new JsonArray()) {
            JsonObject s = skin.getAsJsonObject();
            if ("ACTIVE".equals(s.get("state").getAsString())) {
                skinUrl = s.get("url").getAsString();
                slim = s.has("variant") && "SLIM".equalsIgnoreCase(s.get("variant").getAsString());
            }
        }
        List<Cape> capes = new ArrayList<>();
        for (JsonElement cape : json.has("capes") ? json.getAsJsonArray("capes") : new JsonArray()) {
            JsonObject c = cape.getAsJsonObject();
            capes.add(new Cape(c.get("id").getAsString(), c.has("alias") ? c.get("alias").getAsString() : "Cape",
                    c.get("url").getAsString(), "ACTIVE".equals(c.get("state").getAsString())));
        }
        return new Profile(skinUrl, slim, capes);
    }

    /** Uploads a skin picture and makes it the player's skin in Minecraft. */
    public static void upload(Account account, Path skinFile, boolean slim) throws IOException, InterruptedException {
        // A "multipart" upload, like a web form with a file: the variant, then the picture.
        String boundary = "nexus-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Files.readAllBytes(skinFile));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        send(HttpRequest.newBuilder(URI.create(PROFILE + "/skins"))
                .header("Authorization", "Bearer " + account.accessToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
    }

    /** Wears one of the player's capes (or hides the cape if capeId is null). */
    public static void setCape(Account account, String capeId) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(PROFILE + "/capes/active"))
                .header("Authorization", "Bearer " + account.accessToken());
        if (capeId == null) {
            request.DELETE();
        } else {
            request.header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"capeId\":\"" + capeId + "\"}"));
        }
        send(request.build());
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Couldn't reach Minecraft's servers. Check your internet connection.", e);
        }
        if (response.statusCode() == 401) {
            throw new IOException("Your sign-in has expired – please sign in again.");
        }
        if (response.statusCode() == 400) {
            throw new IOException("Minecraft rejected that skin. Skins must be 64 x 64 (or 64 x 32) PNG pictures.");
        }
        if (response.statusCode() == 429) {
            throw new IOException("Too many skin changes in a row – please wait a minute.");
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Minecraft's servers answered with error " + response.statusCode() + ".");
        }
        return response;
    }

    // ================================================================== the local skin library

    /** A saved skin: the picture file, and whether it's for the slim ("Alex") model. */
    public record SavedSkin(String name, Path file, boolean slim) {}

    private static Path libraryDir() {
        return NexusPaths.root().resolve("skin-library");
    }

    /** All saved skins, newest first. Slim skins are saved with "-slim" at the end of the name. */
    public static List<SavedSkin> library() throws IOException {
        List<SavedSkin> skins = new ArrayList<>();
        if (!Files.isDirectory(libraryDir())) {
            return skins;
        }
        try (Stream<Path> files = Files.list(libraryDir())) {
            files.filter(file -> file.toString().endsWith(".png"))
                    .sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()))
                    .forEach(file -> {
                        String base = file.getFileName().toString().replaceAll("\\.png$", "");
                        boolean slim = base.endsWith("-slim");
                        skins.add(new SavedSkin(slim ? base.substring(0, base.length() - 5) : base, file, slim));
                    });
        }
        return skins;
    }

    /** Copies a skin picture into the library. */
    public static SavedSkin save(Path picture, String name, boolean slim) throws IOException {
        Files.createDirectories(libraryDir());
        String safe = NexusPaths.safeFileName(name.isBlank() ? "Skin" : name);
        Path target = libraryDir().resolve(safe + (slim ? "-slim" : "") + ".png");
        Files.copy(picture, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new SavedSkin(safe, target, slim);
    }

    public static void delete(SavedSkin skin) throws IOException {
        Files.deleteIfExists(skin.file());
    }
}
