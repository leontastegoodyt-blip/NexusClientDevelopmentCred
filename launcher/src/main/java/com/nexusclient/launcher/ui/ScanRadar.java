package com.nexusclient.launcher.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * ScanRadar – the round "radar" on the Error Scan card. While a scan runs, a glowing beam sweeps
 * around it; when it finishes, the middle shows a green tick (all good) or an amber warning.
 * Only shapes and an icon – no text – so it may glow (see the visual rule in the project brief).
 */
public class ScanRadar extends StackPane {

    private final Arc beam = new Arc(0, 0, 34, 34, 90, 70);
    private final StackPane middle = new StackPane();
    private final RotateTransition sweep = new RotateTransition(Duration.seconds(1.1), beam);

    public ScanRadar() {
        getStyleClass().add("scan-radar");
        Circle outer = new Circle(36);
        outer.getStyleClass().add("radar-ring");
        Circle inner = new Circle(22);
        inner.getStyleClass().add("radar-ring-inner");
        beam.setType(ArcType.ROUND);
        beam.getStyleClass().add("radar-beam");
        beam.setVisible(false);
        middle.getChildren().setAll(icon(Icons.ERROR_SCAN, "radar-icon"));
        getChildren().addAll(outer, inner, beam, middle);
        setMinSize(80, 80);
        setMaxSize(80, 80);

        sweep.setByAngle(360);
        sweep.setInterpolator(Interpolator.LINEAR);
        sweep.setCycleCount(Animation.INDEFINITE);
    }

    /** The beam starts sweeping. */
    public void start() {
        beam.setVisible(true);
        middle.getChildren().setAll(icon(Icons.ERROR_SCAN, "radar-icon"));
        getStyleClass().removeAll("radar-ok", "radar-problem");
        if (Animations.enabled) {
            sweep.playFromStart();
        }
    }

    /** The scan finished: a green tick (no problems) or an amber warning. */
    public void finish(boolean allGood) {
        sweep.stop();
        beam.setVisible(false);
        getStyleClass().add(allGood ? "radar-ok" : "radar-problem");
        Node result = icon(allGood ? Icons.CHECK : Icons.ERROR_SCAN, "radar-icon");
        middle.getChildren().setAll(result);
        Animations.flipIn(result, Duration.ZERO);
    }

    private static Node icon(String shape, String style) {
        Node node = Icons.create(shape, 26);
        node.getStyleClass().add(style);
        return node;
    }
}
