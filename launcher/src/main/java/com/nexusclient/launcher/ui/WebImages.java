package com.nexusclient.launcher.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebImages – loads pictures from the internet (like Modrinth mod icons) in the background.
 *
 * JavaFX can only read PNG, JPG, GIF and BMP pictures, but most Modrinth icons are WebP. So this
 * first tries JavaFX, and if that can't read it, uses the TwelveMonkeys WebP reader and copies the
 * pixels across. Pictures are remembered, so switching tabs doesn't download them again.
 */
public final class WebImages {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private WebImages() {}

    /** Loads the picture in the background, then calls whenReady (on the UI thread) if it worked. */
    public static void load(String url, Consumer<Image> whenReady) {
        Image cached = CACHE.get(url);
        if (cached != null) {
            whenReady.accept(cached);
            return;
        }
        Background.run("web-image", () -> {
            Image image = decode(download(url));
            if (image != null) {
                CACHE.put(url, image);
            }
            return image;
        }, image -> {
            if (image != null) {
                whenReady.accept(image);
            }
        }, error -> { }); // no picture – the dark placeholder square just stays
    }

    private static byte[] download(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setRequestProperty("User-Agent", "NexusClient-Launcher");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Turns the downloaded bytes into a JavaFX picture, or null if nothing can read them. */
    private static Image decode(byte[] bytes) throws IOException {
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (!image.isError()) {
            return image;
        }
        // JavaFX couldn't read it (probably WebP) – ask Java's ImageIO, which now knows WebP.
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
        return buffered == null ? null : toFx(buffered);
    }

    /** Copies a Java (AWT) picture into a JavaFX picture, pixel by pixel. */
    private static Image toFx(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, row, 0, width);
            writer.setPixels(0, y, width, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, width);
        }
        return image;
    }
}
