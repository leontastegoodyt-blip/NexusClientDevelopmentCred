package com.nexusclient.launcher;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * DevTour – a developer tool: opens each screen of the launcher in turn, saves a picture of it,
 * then closes the launcher. It lets us check how every page looks without clicking around.
 *
 * Run it with:  gradlew :launcher:run -PnexusTour=C:\some\folder
 * (Players never see this – it only runs when that option is given.)
 */
public final class DevTour {

    /** One stop on the tour: a name for the picture, and what to do to get to that screen. */
    public record Stop(String name, Runnable action, long waitMillis) {}

    private DevTour() {}

    /** The folder to save pictures in, or null if no tour was asked for. */
    public static String folder() {
        return System.getProperty("nexus.tour");
    }

    /** Visits each stop, takes a picture, and closes the launcher at the end. */
    public static void run(Scene scene, List<Stop> stops) {
        File dir = new File(folder());
        dir.mkdirs();
        visit(scene, stops, 0, dir);
    }

    private static void visit(Scene scene, List<Stop> stops, int index, File dir) {
        if (index >= stops.size()) {
            System.out.println("Tour finished: " + stops.size() + " pictures in " + dir);
            Platform.exit();
            return;
        }
        Stop stop = stops.get(index);
        stop.action().run();
        // Wait for animations to settle, then take the picture.
        PauseTransition wait = new PauseTransition(Duration.millis(stop.waitMillis()));
        wait.setOnFinished(event -> {
            save(scene.snapshot(null), new File(dir, String.format("%02d-%s.png", index + 1, stop.name())));
            visit(scene, stops, index + 1, dir);
        });
        wait.play();
    }

    /** Saves a JavaFX picture as a PNG file (converting it to the format Java's image writer uses). */
    private static void save(WritableImage image, File file) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            reader.getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), row, 0, width);
            out.setRGB(0, y, width, 1, row, 0, width);
        }
        try {
            ImageIO.write(out, "png", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
