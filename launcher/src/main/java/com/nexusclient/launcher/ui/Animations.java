package com.nexusclient.launcher.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

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
    private static final String LIFT_KEY = "nexus.liftAnimation";

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
            } else if (target instanceof Node node && isCard(node)) {
                liftTo(node, -3); // cards float up a little under the mouse
            }
        });
        // The mouse moved off something.
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, event -> {
            Object target = event.getTarget();
            if (target instanceof Node node && isAnimatedButton(node)) {
                scaleTo(node, 1.0, HOVER_TIME);
            } else if (target instanceof ListCell<?> cell && cell.getStyleClass().contains("list-cell")) {
                slideTo(cell, 0);
            } else if (target instanceof Node node && isCard(node)) {
                liftTo(node, 0);
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

    /**
     * Opens a page: the page fades and slides in, then its cards "flip in" one after another
     * (see flipInCards).
     */
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
        if (page instanceof Parent parent) {
            flipInCards(parent);
        }
    }

    /**
     * The style classes of "cards" – the boxes that flip in when a page opens and lift up when the
     * mouse is over them. Add "flip-in" to any other box that should do the same.
     */
    private static final List<String> CARD_CLASSES = List.of("flip-in", "mod-row", "settings-card", "news-card",
            "social-card", "screenshot-tile", "stat-tile", "server-tile");

    /** At most this many cards flip in one after another (the rest appear together, so it stays quick). */
    private static final int MAX_STAGGERED = 14;

    /**
     * Makes every card inside this area flip in one after another, like cards being dealt: each one
     * turns from side-on to facing you (rotating around its upright middle line) while fading in.
     *
     * Turning around the upright line in a flat (2D) window makes the card look squashed at the
     * start and full-width at the end – our eyes read that as a flip. It's very cheap to draw.
     * Call it again after filling an area with new cards (e.g. search results).
     */
    public static void flipInCards(Parent area) {
        if (!enabled) {
            return;
        }
        List<Node> cards = new ArrayList<>();
        collectCards(area, cards);
        for (int i = 0; i < cards.size(); i++) {
            flipIn(cards.get(i), Duration.millis(40L * Math.min(i, MAX_STAGGERED)));
        }
    }

    /** One card flipping in after a short wait. */
    public static void flipIn(Node card, Duration delay) {
        if (!enabled) {
            return;
        }
        card.setRotationAxis(Rotate.Y_AXIS);
        card.setRotate(-75);
        card.setOpacity(0);
        RotateTransition turn = new RotateTransition(Duration.millis(420), card);
        turn.setToAngle(0);
        turn.setInterpolator(SPRING);
        FadeTransition fade = new FadeTransition(Duration.millis(260), card);
        fade.setToValue(1);
        ParallelTransition both = new ParallelTransition(turn, fade);
        both.setDelay(delay);
        both.play();
    }

    /** Finds cards inside an area (but not cards inside cards – those flip with their parent). */
    private static void collectCards(Parent parent, List<Node> into) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!child.isVisible()) {
                continue;
            }
            if (isCard(child)) {
                into.add(child);
            } else if (child instanceof Parent inner) {
                collectCards(inner, into);
            }
        }
    }

    private static boolean isCard(Node node) {
        for (String style : node.getStyleClass()) {
            if (CARD_CLASSES.contains(style)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flips a card over to show its other side (like turning a playing card): it turns side-on,
     * swaps what it shows half-way, and turns back. "front" and "back" sit in the same StackPane.
     */
    public static void flipOver(Node card, Node showing, Node hidden) {
        if (!enabled) {
            showing.setVisible(false);
            hidden.setVisible(true);
            return;
        }
        card.setRotationAxis(Rotate.Y_AXIS);
        RotateTransition away = new RotateTransition(Duration.millis(170), card);
        away.setFromAngle(0);
        away.setToAngle(90);
        away.setInterpolator(Interpolator.EASE_IN);
        away.setOnFinished(e -> {
            showing.setVisible(false);
            hidden.setVisible(true);
        });
        RotateTransition back = new RotateTransition(Duration.millis(260), card);
        back.setFromAngle(-90);
        back.setToAngle(0);
        back.setInterpolator(SPRING);
        new SequentialTransition(away, back).play();
    }

    /** Counts a number up from 0 to its value (for stats), e.g. "0 → 12". */
    public static void countUp(Label label, long target, String suffix) {
        if (!enabled || target <= 0) {
            label.setText(target + suffix);
            return;
        }
        LongProperty value = new SimpleLongProperty(0);
        value.addListener((o, a, b) -> label.setText(b + suffix));
        label.setText("0" + suffix);
        Timeline count = new Timeline(new KeyFrame(Duration.millis(900),
                new KeyValue(value, target, Interpolator.EASE_OUT)));
        count.play();
    }

    /**
     * A bright streak that sweeps across a button now and then (used on LAUNCH). The streak sits in
     * a see-through layer on top of the button (inside the same StackPane), clipped to the button's
     * rounded shape, and never catches the mouse. It's a small area, so it's cheap to redraw.
     */
    public static Animation shine(StackPane holder, Region button, double radius) {
        Region streak = new Region();
        streak.getStyleClass().add("shine-streak");
        streak.setManaged(false);
        streak.setRotate(20);

        Pane layer = new Pane(streak);
        layer.setMouseTransparent(true);
        // Exactly the button's size and shape, and it grows/shrinks with the button's hover animation.
        layer.minWidthProperty().bind(button.widthProperty());
        layer.maxWidthProperty().bind(button.widthProperty());
        layer.minHeightProperty().bind(button.heightProperty());
        layer.maxHeightProperty().bind(button.heightProperty());
        layer.scaleXProperty().bind(button.scaleXProperty());
        layer.scaleYProperty().bind(button.scaleYProperty());
        layer.visibleProperty().bind(button.disabledProperty().not());
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(button.widthProperty());
        clip.heightProperty().bind(button.heightProperty());
        layer.setClip(clip);
        button.heightProperty().addListener((o, a, b) -> {
            streak.resize(38, b.doubleValue() * 2);
            streak.setLayoutY(-b.doubleValue() / 2);
        });
        holder.getChildren().add(layer);

        TranslateTransition sweep = new TranslateTransition(Duration.millis(950), streak);
        sweep.setFromX(-90);
        sweep.toXProperty().bind(button.widthProperty().add(50));
        sweep.setInterpolator(Interpolator.EASE_BOTH);
        SequentialTransition loop = new SequentialTransition(sweep, new PauseTransition(Duration.seconds(3.5)));
        loop.setCycleCount(Animation.INDEFINITE);
        streak.setTranslateX(-90);
        if (enabled) {
            loop.play();
        }
        return loop;
    }

    /** An "overshoot" curve: moves fast, goes a tiny bit too far, then settles – feels springy. */
    public static final Interpolator SPRING = new Interpolator() {
        @Override
        protected double curve(double t) {
            double s = 1.4; // how far it overshoots
            double x = t - 1;
            return x * x * ((s + 1) * x + s) + 1;
        }
    };

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

    /** Smoothly moves a card up (negative) or back down to its place (0). */
    private static void liftTo(Node node, double y) {
        if (!enabled) {
            return;
        }
        if (node.getProperties().get(LIFT_KEY) instanceof TranslateTransition running) {
            running.stop();
        }
        TranslateTransition transition = new TranslateTransition(HOVER_TIME, node);
        transition.setToY(y);
        transition.setInterpolator(Interpolator.EASE_OUT);
        node.getProperties().put(LIFT_KEY, transition);
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
