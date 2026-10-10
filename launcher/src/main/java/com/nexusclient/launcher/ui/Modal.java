package com.nexusclient.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Modal – a pop-up card in the middle of the window, over a darkened background.
 * Used for instance settings, confirmations ("Delete this?") and small forms.
 *
 * Only one is open at a time. Press Esc or click the dark background to close it.
 */
public final class Modal {

    /** The layer modals appear on – the window's top-level StackPane (set up in NexusLauncher). */
    private static StackPane layer;
    private static StackPane current;

    private Modal() {}

    public static void setLayer(StackPane modalLayer) {
        layer = modalLayer;
    }

    /** Shows content in a pop-up card (closing any other one first). */
    public static void show(Node content, double width) {
        close();
        VBox card = new VBox(content);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(width);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane backdrop = new StackPane(card);
        backdrop.getStyleClass().add("modal-backdrop");
        // Clicking the dark area (not the card) closes it.
        backdrop.setOnMouseClicked(event -> {
            if (event.getTarget() == backdrop) {
                close();
            }
        });
        backdrop.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
            }
        });

        current = backdrop;
        layer.getChildren().add(backdrop);
        backdrop.requestFocus();
        if (Animations.enabled) {
            backdrop.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(160), backdrop);
            fade.setToValue(1);
            fade.play();
            // The card springs open: starts a little small, overshoots slightly, settles.
            card.setScaleX(0.9);
            card.setScaleY(0.9);
            javafx.animation.ScaleTransition pop = new javafx.animation.ScaleTransition(Duration.millis(320), card);
            pop.setToX(1);
            pop.setToY(1);
            pop.setInterpolator(Animations.SPRING);
            pop.play();
        }
    }

    /** Closes the open pop-up, if there is one. */
    public static void close() {
        if (current != null && layer != null) {
            layer.getChildren().remove(current);
        }
        current = null;
    }

    /**
     * A "Are you sure?" pop-up with a red confirm button and a Cancel button.
     *
     * @param onConfirm what to do if the player confirms
     */
    public static void confirm(String title, String message, String confirmLabel, Runnable onConfirm) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("torcher-title");
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("torcher-text");
        messageLabel.setWrapText(true);

        Button confirm = new Button(confirmLabel);
        confirm.getStyleClass().add("danger-button");
        confirm.setOnAction(event -> {
            close();
            onConfirm.run();
        });
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> close());
        HBox buttons = new HBox(10, cancel, confirm);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        show(new VBox(14, titleLabel, messageLabel, buttons), 460);
    }
}
