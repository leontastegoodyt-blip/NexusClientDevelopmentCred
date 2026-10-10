package com.nexusclient.launcher.ui;

import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.io.InputStream;

/**
 * NexusLogo – the Nexus logo (a glowing hexagon with a stylised N), drawn with shapes so it stays
 * sharp at ANY size – from a tiny badge next to a name up to the welcome screen.
 *
 * It's a redraw of the owner's logo picture (resources/…/logo.png). The picture is used where a
 * normal image fits (the window/taskbar icon, see {@link #appIcon()}); this class is used for the
 * small sizes, where a scaled-down picture would look blurry.
 *
 * Three looks:
 *   - BLUE:    the normal logo
 *   - PREMIUM: gold, with a "+" in the bottom-right corner (Nexus Premium players)
 *   - DEV:     the normal blue logo – the owner's badge (the "DEV" tag next to it is added by
 *              whoever shows the badge)
 */
public class NexusLogo extends Pane {

    public enum Style { BLUE, PREMIUM, DEV }

    // All shapes are drawn on a 512 x 512 canvas (the size of the original picture) and scaled.
    private static final double CANVAS = 512;
    private static final String HEXAGON = "M256 84 L405 170 L405 342 L256 428 L107 342 L107 170 Z";
    private static final String LETTER_N = "M172 192 L190 175 L212 175 L305 290 L305 193 L338 176 L338 336"
            + " L322 352 L300 352 L207 240 L207 318 L172 334 Z";

    /**
     * @param size  width and height in pixels
     * @param style which look (see above)
     * @param glow  true for the soft neon glow (big logos); false for tiny badges
     */
    public NexusLogo(double size, Style style, boolean glow) {
        boolean gold = style == Style.PREMIUM;
        Paint letterPaint = gradient(gold ? "#FFE9A3" : "#5FD3FF", gold ? "#E0A022" : "#2F6BFF");
        Paint edgePaint = gradient(gold ? "#FFD46B" : "#5FD3FF", gold ? "#D18A12" : "#3A7BFF");

        SVGPath hexagon = new SVGPath();
        hexagon.setContent(HEXAGON);
        hexagon.setFill(Color.web(gold ? "#2A1E05" : "#06142A", 0.55)); // faint dark inside
        hexagon.setStroke(edgePaint);
        hexagon.setStrokeWidth(size < 24 ? 26 : 13); // thicker lines when tiny, so they don't vanish
        hexagon.setStrokeLineJoin(StrokeLineJoin.ROUND);

        SVGPath letter = new SVGPath();
        letter.setContent(LETTER_N);
        letter.setFill(letterPaint);

        Group shapes = new Group(hexagon, letter);
        if (size >= 24) {
            // The thin dark slit along the N's diagonal (too small to see on tiny badges).
            Line slit = new Line(226, 207, 298, 297);
            slit.setStroke(Color.web("#06101F"));
            slit.setStrokeWidth(5);
            slit.setStrokeLineCap(StrokeLineCap.ROUND);
            shapes.getChildren().add(slit);
        }
        if (gold) {
            // Premium: a gold "+" in a dark circle in the bottom-right corner.
            Circle plusBack = new Circle(410, 410, 82, Color.web("#0B0B12"));
            plusBack.setStroke(edgePaint);
            plusBack.setStrokeWidth(14);
            SVGPath plus = new SVGPath();
            plus.setContent("M396 360 H424 V396 H460 V424 H424 V460 H396 V424 H360 V396 H396 Z");
            plus.setFill(letterPaint);
            shapes.getChildren().addAll(plusBack, plus);
        }
        if (glow) {
            // Glow is fine here: the logo has no text in it (see the visual rule in the project brief).
            shapes.setEffect(new DropShadow(size * 0.18, Color.web(gold ? "#FFB52E" : "#3AA8FF", 0.75)));
        }
        shapes.getTransforms().add(new Scale(size / CANVAS, size / CANVAS, 0, 0));

        getChildren().add(shapes);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setMouseTransparent(true);
        getStyleClass().add("nexus-logo");
    }

    /** A diagonal gradient from the top-left colour to the bottom-right colour, like the logo. */
    private static Paint gradient(String topLeft, String bottomRight) {
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(topLeft)), new Stop(1, Color.web(bottomRight)));
    }

    /** The owner's logo picture, for the window and taskbar icon (null if it's missing). */
    public static Image appIcon() {
        try (InputStream in = NexusLogo.class.getResourceAsStream("/com/nexusclient/launcher/logo.png")) {
            return in == null ? null : new Image(in);
        } catch (Exception e) {
            return null;
        }
    }
}
