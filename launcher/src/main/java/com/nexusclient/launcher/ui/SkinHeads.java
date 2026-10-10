package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.account.AccountManager.SavedAccount;
import com.nexusclient.launcher.game.Downloader;
import com.nexusclient.launcher.game.NexusPaths;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkinHeads – shows a player's Minecraft head (the face from their skin) next to their name.
 *
 * A Minecraft skin is a small 64 x 64 picture laid out like a paper model. The face is the
 * 8 x 8 square starting at (8, 8), and the hat/hair layer sits on top of it at (40, 8).
 * We cut those out, stack them, and blow them up to the size we want – keeping the pixels sharp
 * ("nearest neighbour" scaling) so it still looks like Minecraft pixel art.
 *
 * Skins are downloaded once and cached in the Nexus "skins" folder.
 */
public final class SkinHeads {

    /** Heads already made, so we don't redo the work. Key: skin link + size. */
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private SkinHeads() {}

    /**
     * Makes a square head picture for an account. Shows a plain placeholder straight away, then
     * swaps in the real head once the skin has downloaded (in the background).
     *
     * @param size width and height in pixels
     */
    public static StackPane create(SavedAccount account, double size) {
        Region placeholder = new Region();
        placeholder.getStyleClass().add("head-placeholder");

        ImageView view = new ImageView();
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(false); // keep the pixels sharp

        StackPane head = new StackPane(placeholder, view);
        head.getStyleClass().add("skin-head");
        head.setMinSize(size, size);
        head.setPrefSize(size, size);
        head.setMaxSize(size, size);
        // Rounded corners.
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size * 0.35);
        clip.setArcHeight(size * 0.35);
        head.setClip(clip);

        if (account != null && account.skinUrl() != null) {
            String skinUrl = account.skinUrl();
            int pixels = (int) Math.round(size * 2); // extra detail for high-resolution screens
            Image cached = CACHE.get(skinUrl + "@" + pixels);
            if (cached != null) {
                view.setImage(cached);
            } else {
                Thread loader = new Thread(() -> {
                    Image headImage = loadHead(skinUrl, pixels);
                    if (headImage != null) {
                        CACHE.put(skinUrl + "@" + pixels, headImage);
                        Platform.runLater(() -> view.setImage(headImage));
                    }
                }, "skin-head-loader");
                loader.setDaemon(true);
                loader.start();
            }
        }
        return head;
    }

    /** Skin links found for players (UUID → skin URL), so each player is only looked up once. */
    private static final Map<String, String> SKIN_URLS = new ConcurrentHashMap<>();

    /**
     * A head for ANY player (friends, clan members…), by their Minecraft UUID. Asks Mojang's public
     * profile service where their skin is, then works like {@link #create}.
     */
    public static StackPane forUuid(String uuid, double size) {
        StackPane head = create(null, size);
        ImageView view = (ImageView) head.getChildren().get(1);
        int pixels = (int) Math.round(size * 2);
        Thread loader = new Thread(() -> {
            String skinUrl = SKIN_URLS.computeIfAbsent(uuid, SkinHeads::lookUpSkinUrl);
            if (skinUrl.isEmpty()) {
                return;
            }
            Image image = CACHE.computeIfAbsent(skinUrl + "@" + pixels, key -> loadHead(skinUrl, pixels));
            if (image != null) {
                Platform.runLater(() -> view.setImage(image));
            }
        }, "skin-head-loader");
        loader.setDaemon(true);
        loader.start();
        return head;
    }

    /** Mojang's profile for a UUID holds the skin link (base64-encoded JSON). "" if there's none. */
    private static String lookUpSkinUrl(String uuid) {
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<String> response = http.send(java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                            + uuid.replace("-", ""))).timeout(java.time.Duration.ofSeconds(10)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "";
            }
            var profile = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            for (var property : profile.getAsJsonArray("properties")) {
                var p = property.getAsJsonObject();
                if ("textures".equals(p.get("name").getAsString())) {
                    String json = new String(java.util.Base64.getDecoder().decode(p.get("value").getAsString()),
                            StandardCharsets.UTF_8);
                    var skin = com.google.gson.JsonParser.parseString(json).getAsJsonObject()
                            .getAsJsonObject("textures").getAsJsonObject("SKIN");
                    return skin == null ? "" : skin.get("url").getAsString();
                }
            }
        } catch (Exception e) {
            // No head then – the placeholder stays.
        }
        return "";
    }

    /** Downloads (or reads the cached) skin and cuts the head out of it. Returns null on failure. */
    private static Image loadHead(String skinUrl, int pixels) {
        try {
            Path file = NexusPaths.root().resolve("skins").resolve(fingerprint(skinUrl) + ".png");
            try (Downloader downloader = new Downloader()) {
                downloader.download(new Downloader.Job(skinUrl, file, null, 0));
            }
            Image skin;
            try (InputStream in = Files.newInputStream(file)) {
                skin = new Image(in);
            }
            if (skin.isError() || skin.getWidth() < 64) {
                return null;
            }
            return cutHead(skin, pixels);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Cuts the face and hat layer out of a skin and scales them up to pixels x pixels. */
    public static Image cutHead(Image skin, int pixels) {
        PixelReader reader = skin.getPixelReader();
        WritableImage head = new WritableImage(pixels, pixels);
        PixelWriter writer = head.getPixelWriter();
        for (int y = 0; y < pixels; y++) {
            for (int x = 0; x < pixels; x++) {
                int skinX = x * 8 / pixels; // which of the 8 x 8 face pixels this is
                int skinY = y * 8 / pixels;
                int face = reader.getArgb(8 + skinX, 8 + skinY);
                int hat = reader.getArgb(40 + skinX, 8 + skinY);
                boolean hatVisible = (hat >>> 24) > 0; // the top 8 bits are see-through-ness
                writer.setArgb(x, y, hatVisible ? hat : face | 0xFF000000);
            }
        }
        return head;
    }

    /** A short, file-name-safe fingerprint of a link, so each skin gets its own cache file. */
    private static String fingerprint(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
