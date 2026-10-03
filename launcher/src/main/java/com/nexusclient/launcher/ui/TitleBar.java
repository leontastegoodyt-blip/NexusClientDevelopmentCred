package com.nexusclient.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * TitleBar – the dark bar across the top of the window.
 *
 * Windows' normal title bar is white and can't be recoloured, so Nexus hides it and draws its own
 * (like Lunar and Feather do). That means we have to do the title bar's jobs ourselves:
 *   - drag the bar to move the window
 *   - double-click it to maximise / restore
 *   - minimise, maximise and close buttons
 * It also holds the account button in the corner (see AccountMenu).
 */
public class TitleBar extends HBox {

    private final Stage stage;

    // Where the mouse grabbed the window, so the window follows the mouse while dragging.
    private double grabX;
    private double grabY;

    // Our own "maximised" state (see toggleMaximize for why), and the size to go back to.
    private boolean maximized = false;
    private Rectangle2D restoreBounds;

    /**
     * @param accountButton the account button to show in the corner (from AccountMenu)
     */
    public TitleBar(Stage stage, Node accountButton) {
        this.stage = stage;
        getStyleClass().add("title-bar");

        // --- Left side: the logo. This whole stretch is the part you can drag. ---
        Label logo = new Label("NEXUS");
        logo.getStyleClass().add("title-logo");
        Label client = new Label("CLIENT");
        client.getStyleClass().add("title-client");

        HBox dragArea = new HBox(6, logo, client);
        dragArea.getStyleClass().add("title-drag-area");
        HBox.setHgrow(dragArea, Priority.ALWAYS); // stretch to fill all the free space
        enableDragging(dragArea);

        // --- Right side: account button, then the window buttons ---
        HBox.setMargin(accountButton, new Insets(0, 10, 0, 0)); // a little gap before the window buttons

        Button minimize = windowButton(Icons.MINIMIZE, "Minimise");
        minimize.setOnAction(event -> stage.setIconified(true));

        Button maximize = windowButton(Icons.MAXIMIZE, "Maximise");
        maximize.setOnAction(event -> toggleMaximize());

        Button close = windowButton(Icons.CLOSE, "Close");
        close.getStyleClass().add("close-button"); // turns red on hover, like Windows
        close.setOnAction(event -> stage.close());

        getChildren().addAll(dragArea, accountButton, minimize, maximize, close);
    }

    /** True while the window fills the screen. The edge-resizer turns itself off then. */
    public boolean isMaximized() {
        return maximized;
    }

    /** Lets the player move the window by dragging, and maximise it by double-clicking. */
    private void enableDragging(HBox dragArea) {
        dragArea.setOnMousePressed(event -> {
            grabX = event.getScreenX() - stage.getX();
            grabY = event.getScreenY() - stage.getY();
        });
        dragArea.setOnMouseDragged(event -> {
            if (!maximized) {
                stage.setX(event.getScreenX() - grabX);
                stage.setY(event.getScreenY() - grabY);
            }
        });
        dragArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }

    /**
     * Maximise the window, or put it back to its old size if it's already maximised.
     *
     * We don't use JavaFX's built-in stage.setMaximized() because, for a window without the normal
     * title bar, it covers the Windows taskbar too. Instead we fill the screen's "visual bounds",
     * which is the screen minus the taskbar.
     */
    private void toggleMaximize() {
        if (maximized) {
            stage.setX(restoreBounds.getMinX());
            stage.setY(restoreBounds.getMinY());
            stage.setWidth(restoreBounds.getWidth());
            stage.setHeight(restoreBounds.getHeight());
            maximized = false;
        } else {
            restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            // Use whichever monitor the window is currently on.
            Screen screen = Screen.getScreensForRectangle(restoreBounds).stream()
                    .findFirst()
                    .orElse(Screen.getPrimary());
            Rectangle2D area = screen.getVisualBounds();
            stage.setX(area.getMinX());
            stage.setY(area.getMinY());
            stage.setWidth(area.getWidth());
            stage.setHeight(area.getHeight());
            maximized = true;
        }
    }

    /** Makes one of the minimise / maximise / close buttons. */
    private Button windowButton(String icon, String tooltip) {
        Button button = new Button();
        button.setGraphic(Icons.create(icon, 14));
        button.getStyleClass().add("window-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false); // don't let the Tab key land on these
        return button;
    }
}
