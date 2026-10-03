package com.nexusclient.launcher.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Animations – the launcher's small movements:
 *
 *   - every button smoothly grows a little when the mouse is over it, and presses in when clicked
 *   - instance rows slide slightly to the right when the mouse is over them
 *   - pages fade and slide in when you switch tabs
 *   - a slow "breathing" fade, used for the glow behind LAUNCH
 *
 * The button animations are installed ONCE for the whole window (see installButtonAnimations), so
 * every button gets them automatically – including buttons that are added later.
 *
 * (Newer JavaFX versions can animate straight from CSS; ours (21) can't, so we do it in Java.)
 */
public final class Animations {

    /**
     * Master switch. The "reduced motion" accessibility setting (the project brief, Settings) will turn this
     * off for players who get dizzy or distracted by movement.
     */
    public static boolean enabled = true;

    private static final Duration HOVER_TIME = Duration.millis(140);
    private static final Duration PRESS_TIME = Duration.millis(80);
    private static final double PRESS_SCALE = 0.94;

    /** Each node remembers its running scale/slide animation here, so a new one can stop the old one. */
    private static final String SCALE_KEY = "nexus.scaleAnimation";
    private static final String SLIDE_KEY = "nexus.slideAnimation";

    private Animations() {}

    /**
     * Turns on hover and click animations for every button in the window.
     *
     * It uses "event filters" on the whole scene: the scene hears about every mouse event before the
     * buttons do, works out whether the mouse is on a button, and animates that button.
     */
    public static void installButtonAnimations(Scene scene) {
        // The mouse moved onto something.
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, event -> {
            Object target = event.getTarget();
            if (target instanceof Node node && isAnimatedButton(node)) {
                scaleTo(node, hoverScale(node), HOVER_TIME);
            } else if (target instanceof ListCell<?> cell && isInstanceRow(cell)) {
                slideTo(cell, 4);
            }
        });
        // The mouse moved off something.
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, event -> {
            Object target = event.getTarget();
            if (target instanceof Node node && isAnimatedButton(node)) {
                scaleTo(node, 1.0, HOVER_TIME);
            } else if (target instanceof ListCell<?> cell && cell.getStyleClass().contains("list-cell")) {
                slideTo(cell, 0);
            }
        });
        // A mouse button was pressed – on the button itself, or on its icon or text.
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Node button = findButton(event.getPickResult().getIntersectedNode());
            if (button != null) {
                scaleTo(button, PRESS_SCALE, PRESS_TIME);
            }
        });
        // ...and released: spring back to the hover size (or normal size if the mouse left).
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            Node button = findButton(event.getPickResult().getIntersectedNode());
            if (button != null) {
                scaleTo(button, button.isHover() ? hoverScale(button) : 1.0, HOVER_TIME);
            }
        });
    }

    /** Fades and slides a page in when its tab is opened. */
    public static void pageIn(Node page) {
        if (!enabled) {
            return;
        }
        page.setOpacity(0);
        page.setTranslateY(10);
        FadeTransition fade = new FadeTransition(Duration.millis(220), page);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), page);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, slide).play();
    }

    /**
     * Makes a node slowly fade between two see-through levels, forever – like breathing.
     * Returns the animation so it can be stopped.
     */
    public static FadeTransition breathe(Node node, double from, double to, Duration halfCycle) {
        FadeTransition breathe = new FadeTransition(halfCycle, node);
        breathe.setFromValue(from);
        breathe.setToValue(to);
        breathe.setAutoReverse(true);                // fade out, then back in
        breathe.setCycleCount(Animation.INDEFINITE); // forever
        breathe.setInterpolator(Interpolator.EASE_BOTH);
        if (enabled) {
            breathe.play();
        } else {
            node.setOpacity(to);
        }
        return breathe;
    }

    // ------------------------------------------------------------------ helpers

    /** True for real, clickable buttons (and dropdowns). The title bar's window buttons stay still. */
    private static boolean isAnimatedButton(Node node) {
        return (node instanceof ButtonBase || node instanceof ComboBoxBase)
                && !node.isDisabled()
                && !node.getStyleClass().contains("window-button");
    }

    /** True for a filled row in the Instances list. */
    private static boolean isInstanceRow(ListCell<?> cell) {
        return !cell.isEmpty() && cell.getListView() != null
                && cell.getListView().getStyleClass().contains("instance-list");
    }

    /** Walks up from what was clicked (maybe a button's icon or text) to the button itself. */
    private static Node findButton(Node node) {
        for (int depth = 0; node != null && depth < 6; depth++) {
            if (isAnimatedButton(node)) {
                return node;
            }
            node = node.getParent();
        }
        return null;
    }

    /** How much each kind of button grows on hover. Big buttons grow less, so it still looks subtle. */
    private static double hoverScale(Node node) {
        if (node.getStyleClass().contains("launch-button")) {
            return 1.03;
        }
        if (node.getStyleClass().contains("nav-button")) {
            return 1.06;
        }
        return 1.05;
    }

    /** Smoothly changes a node's size to the given scale (1.0 = normal size). */
    private static void scaleTo(Node node, double scale, Duration time) {
        if (!enabled) {
            return;
        }
        if (node.getProperties().get(SCALE_KEY) instanceof ScaleTransition running) {
            running.stop();
        }
        ScaleTransition transition = new ScaleTransition(time, node);
        transition.setToX(scale);
        transition.setToY(scale);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        node.getProperties().put(SCALE_KEY, transition);
        transition.play();
    }

    /** Smoothly slides a node sideways to the given distance in pixels (0 = back in place). */
    private static void slideTo(Node node, double x) {
        if (!enabled) {
            return;
        }
        if (node.getProperties().get(SLIDE_KEY) instanceof TranslateTransition running) {
            running.stop();
        }
        TranslateTransition transition = new TranslateTransition(HOVER_TIME, node);
        transition.setToX(x);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        node.getProperties().put(SLIDE_KEY, transition);
        transition.play();
    }
}
