package com.nexusclient.launcher.ui;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.function.BooleanSupplier;

/**
 * WindowResizer – lets the player resize the window by dragging its edges and corners.
 *
 * Normally Windows does this for us, but Nexus draws its own title bar (see TitleBar), and
 * a window without the normal frame loses edge-resizing. So we rebuild it:
 *   1. When the mouse is near an edge, show a resize cursor (↔, ↕ or diagonal).
 *   2. When the player presses there and drags, change the window's size and position.
 */
public final class WindowResizer {

    /** How close to the edge (in pixels) the mouse has to be to start resizing. */
    private static final double EDGE = 6;

    private final Stage stage;
    private final BooleanSupplier enabled;

    // Which edges the mouse is on right now.
    private boolean left, right, top, bottom;
    // True while the player is dragging an edge.
    private boolean resizing = false;
    // The mouse position and window bounds at the moment the drag started.
    private double pressX, pressY, startX, startY, startWidth, startHeight;

    private WindowResizer(Stage stage, BooleanSupplier enabled) {
        this.stage = stage;
        this.enabled = enabled;
    }

    /**
     * Turns on edge-resizing for a window.
     *
     * @param enabled asked before each resize; return false to switch resizing off
     *                (for example while the window is maximised)
     */
    public static void install(Stage stage, Scene scene, BooleanSupplier enabled) {
        WindowResizer resizer = new WindowResizer(stage, enabled);

        // "Event filters" see mouse events BEFORE the buttons and lists inside the window do,
        // so we can grab a press on the edge before anything else reacts to it.
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> resizer.onMoved(scene, event));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, resizer::onPressed);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, resizer::onDragged);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> resizer.resizing = false);
    }

    /** Work out which edges the mouse is on, and show the matching cursor. */
    private void onMoved(Scene scene, MouseEvent event) {
        if (!enabled.getAsBoolean()) {
            left = right = top = bottom = false;
        } else {
            double x = event.getSceneX();
            double y = event.getSceneY();
            left = x < EDGE;
            right = x > scene.getWidth() - EDGE;
            top = y < EDGE;
            bottom = y > scene.getHeight() - EDGE;
        }
        scene.setCursor(cursorForEdges());
    }

    private void onPressed(MouseEvent event) {
        if (!(left || right || top || bottom)) {
            return; // not on an edge: let the click through to whatever is under it
        }
        resizing = true;
        pressX = event.getScreenX();
        pressY = event.getScreenY();
        startX = stage.getX();
        startY = stage.getY();
        startWidth = stage.getWidth();
        startHeight = stage.getHeight();
        event.consume(); // "consume" = this event is handled, don't pass it on
    }

    private void onDragged(MouseEvent event) {
        if (!resizing) {
            return;
        }
        double dx = event.getScreenX() - pressX; // how far the mouse moved since the press
        double dy = event.getScreenY() - pressY;

        if (right) {
            stage.setWidth(Math.max(stage.getMinWidth(), startWidth + dx));
        }
        if (bottom) {
            stage.setHeight(Math.max(stage.getMinHeight(), startHeight + dy));
        }
        if (left) {
            // Dragging the left edge moves the window's left side AND changes its width.
            double newWidth = Math.max(stage.getMinWidth(), startWidth - dx);
            stage.setX(startX + startWidth - newWidth);
            stage.setWidth(newWidth);
        }
        if (top) {
            double newHeight = Math.max(stage.getMinHeight(), startHeight - dy);
            stage.setY(startY + startHeight - newHeight);
            stage.setHeight(newHeight);
        }
        event.consume();
    }

    /** The resize cursor for the edge or corner the mouse is on (null = normal cursor). */
    private Cursor cursorForEdges() {
        if (top && left) return Cursor.NW_RESIZE;
        if (top && right) return Cursor.NE_RESIZE;
        if (bottom && left) return Cursor.SW_RESIZE;
        if (bottom && right) return Cursor.SE_RESIZE;
        if (left) return Cursor.W_RESIZE;
        if (right) return Cursor.E_RESIZE;
        if (top) return Cursor.N_RESIZE;
        if (bottom) return Cursor.S_RESIZE;
        return null;
    }
}
