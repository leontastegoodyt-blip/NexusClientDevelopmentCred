package com.nexusclient.launcher.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * NeonBackdrop – the neon background behind the whole launcher:
 *   - a few big, soft neon glows (cyan, violet, magenta)
 *   - a faint "synthwave" grid floor fading in at the bottom
 *
 * The panels on top are partly see-through ("glass"), so the glow shows through them.
 * It doesn't move: a moving background makes the whole window redraw every frame, which made the
 * launcher laggy and its glowing text blocky. Still glows give the same look for free.
 */
public class NeonBackdrop extends Pane {

    /** One glow: its circle, and where it sits (as a fraction of the window size). */
    private record Glow(Circle circle, double fx, double fy, double radiusFraction) {}

    private static final Color VIOLET = Color.web("#8B5CFF");
    private static final Color MAGENTA = Color.web("#FF2BD6");

    private final Canvas grid = new Canvas();
    private final List<Glow> glows = new ArrayList<>();
    private Color accent = Color.web("#00E5FF");

    public NeonBackdrop() {
        getStyleClass().add("neon-backdrop");
        setMouseTransparent(true); // clicks go straight through to the launcher

        // Where each glow lives (fx, fy = position as a fraction of the window) and how big it is.
        addGlow(0.92, 0.05, 0.55, 0.20); // accent colour, top right
        addGlow(0.10, 0.95, 0.60, 0.16); // violet, bottom left
        addGlow(0.55, 0.55, 0.45, 0.08); // magenta, middle
        addGlow(0.30, 0.10, 0.35, 0.10); // accent, top left (small)
        recolour();

        getChildren().add(0, grid);

        // Cut the glows off at the window's edge (they're bigger than the window),
        // so they never make the backdrop – or the window – bigger than it should be.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        setMinSize(0, 0);
        setPrefSize(0, 0); // fill whatever space the window gives, never ask for more
        widthProperty().addListener((o, a, b) -> layoutGlows());
        heightProperty().addListener((o, a, b) -> layoutGlows());
    }

    private void addGlow(double fx, double fy, double radiusFraction, double strength) {
        Circle circle = new Circle();
        circle.setUserData(strength);
        circle.setCache(true); // draw the soft glow once, then reuse the picture
        glows.add(new Glow(circle, fx, fy, radiusFraction));
        getChildren().add(circle);
    }

    /** Uses the player's accent colour (Settings → Appearance) for the main glows. */
    public void setAccent(Color colour) {
        accent = colour;
        recolour();
        drawGrid();
    }


    private void recolour() {
        Color[] colours = {accent, VIOLET, MAGENTA, accent};
        for (int i = 0; i < glows.size(); i++) {
            Circle circle = glows.get(i).circle();
            double strength = (double) circle.getUserData();
            Color c = colours[i % colours.length];
            // Bright in the middle, fading smoothly to nothing at the edge.
            circle.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, c.deriveColor(0, 1, 1, strength)),
                    new Stop(0.45, c.deriveColor(0, 1, 1, strength * 0.45)),
                    new Stop(1, c.deriveColor(0, 1, 1, 0))));
        }
    }

    private void layoutGlows() {
        double w = getWidth();
        double h = getHeight();
        double size = Math.max(w, h);
        for (Glow glow : glows) {
            glow.circle().setRadius(size * glow.radiusFraction());
            glow.circle().setCenterX(w * glow.fx());
            glow.circle().setCenterY(h * glow.fy());
        }
        grid.setWidth(w);
        grid.setHeight(h);
        drawGrid();
    }

    /** The grid floor: lines running into the distance, fading out towards the horizon. */
    private void drawGrid() {
        double w = grid.getWidth();
        double h = grid.getHeight();
        GraphicsContext g = grid.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        if (w <= 0 || h <= 0) {
            return;
        }
        double horizon = h * 0.62;
        double floor = h - horizon;
        // Lines are brighter near the viewer (bottom) and fade towards the horizon.
        g.setStroke(new LinearGradient(0, horizon, 0, h, false, CycleMethod.NO_CYCLE,
                new Stop(0, accent.deriveColor(0, 1, 1, 0)),
                new Stop(1, accent.deriveColor(0, 1, 1, 0.10))));
        g.setLineWidth(1);
        // Horizontal lines, closer together towards the horizon (like real perspective).
        for (int i = 1; i <= 14; i++) {
            double t = i / 14.0;
            double y = horizon + floor * t * t;
            g.strokeLine(0, y, w, y);
        }
        // Lines fanning out from the middle of the horizon.
        double centre = w / 2;
        for (int i = -16; i <= 16; i++) {
            g.strokeLine(centre + i * 18, horizon, centre + i * w / 9.0, h);
        }
    }
}
