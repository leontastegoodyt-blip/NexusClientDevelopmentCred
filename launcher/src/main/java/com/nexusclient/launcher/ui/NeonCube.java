package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.settings.SettingsStore;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

/**
 * NeonCube – the decoration in the Home banner: a glowing outline of a block (seen from above,
 * like a Minecraft block) with a smaller one floating beside it. Drawn from simple shapes – no
 * Minecraft artwork is used. It slowly bobs up and down unless "reduced motion" is on.
 *
 * Colours come from theme.css (.neon-cube-line and .neon-cube-face-*), so it follows the
 * player's accent colour.
 */
public class NeonCube extends Pane {

    public NeonCube() {
        setMouseTransparent(true);
        setPrefSize(190, 150);
        setMinSize(190, 150);
        setMaxSize(190, 150);

        Pane big = cube(52);
        big.setLayoutX(48);
        big.setLayoutY(22);
        Pane small = cube(22);
        small.setLayoutX(150);
        small.setLayoutY(8);
        small.setOpacity(0.8);
        getChildren().addAll(big, small);

        // A soft violet glow around the lines.
        setEffect(new DropShadow(22, Color.web("#8B5CFF", 0.65)));

        // Bob the big cube up and down, and the small one at a different speed so it feels alive.
        TranslateTransition bigFloat = new TranslateTransition(Duration.seconds(3.2), big);
        bigFloat.setByY(-8);
        TranslateTransition smallFloat = new TranslateTransition(Duration.seconds(2.3), small);
        smallFloat.setByY(10);
        RotateTransition smallSpin = new RotateTransition(Duration.seconds(9), small);
        smallSpin.setByAngle(14);
        ParallelTransition motion = new ParallelTransition(bigFloat, smallFloat, smallSpin);
        for (Animation part : new Animation[] {bigFloat, smallFloat, smallSpin}) {
            part.setAutoReverse(true);
            part.setCycleCount(Animation.INDEFINITE);
            ((javafx.animation.Transition) part).setInterpolator(Interpolator.EASE_BOTH);
        }
        // Read the setting directly: this is built before Theme applies "reduced motion".
        if (!SettingsStore.get().reducedMotion) {
            motion.play();
        }
    }

    /**
     * One block seen at an angle: a top face and two side faces (each a 4-cornered shape).
     * "s" is half the block's width.
     */
    private static Pane cube(double s) {
        double h = s * 0.58;      // how tall the top face looks from this angle
        double depth = s * 1.15;  // how tall the sides are
        Polygon top = face("neon-cube-face-top", 0, h, s, 0, 2 * s, h, s, 2 * h);
        Polygon left = face("neon-cube-face-left", 0, h, s, 2 * h, s, 2 * h + depth, 0, h + depth);
        Polygon right = face("neon-cube-face-right", s, 2 * h, 2 * s, h, 2 * s, h + depth, s, 2 * h + depth);
        return new Pane(left, right, top);
    }

    private static Polygon face(String styleClass, double... corners) {
        Polygon face = new Polygon(corners);
        face.getStyleClass().addAll("neon-cube-line", styleClass);
        return face;
    }
}
