package com.nexusclient.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Toast – a small message card that pops up in the bottom-right corner for a few seconds,
 * e.g. "Signed in as Steve" or an error. Click it to close it early.
 *
 * (Named after toast popping out of a toaster.)
 */
public final class Toast {

    /** The layer toasts appear on – the window's top-level StackPane (set up in NexusLauncher). */
    private static StackPane layer;

    private Toast() {}

    public static void setLayer(StackPane toastLayer) {
        layer = toastLayer;
    }

    /** A normal message. */
    public static void info(String message) {
        show(message, false);
    }

    /** An error message (red edge, stays a little longer). */
    public static void error(String message) {
        show(message, true);
    }

    private static void show(String message, boolean isError) {
        if (layer == null) {
            System.err.println(message);
            return;
        }
        Label toast = new Label(message);
        toast.getStyleClass().addAll("toast", isError ? "toast-error" : "toast-info");
        toast.setWrapText(true);
        toast.setMaxWidth(420);
        toast.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 28, 44, 0));
        layer.getChildren().add(toast);

        // Fade in, wait, fade out, then remove it.
        toast.setOpacity(0);
        FadeTransition in = new FadeTransition(Duration.millis(200), toast);
        in.setToValue(1);
        PauseTransition wait = new PauseTransition(Duration.seconds(isError ? 9 : 5));
        FadeTransition out = new FadeTransition(Duration.millis(300), toast);
        out.setToValue(0);
        SequentialTransition life = new SequentialTransition(in, wait, out);
        life.setOnFinished(event -> layer.getChildren().remove(toast));
        life.play();

        toast.setOnMouseClicked(event -> {
            life.stop();
            layer.getChildren().remove(toast);
        });
    }
}
