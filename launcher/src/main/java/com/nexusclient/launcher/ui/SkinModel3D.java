package com.nexusclient.launcher.ui;

import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

/**
 * SkinModel3D – a 3D Minecraft player wearing a skin, which the player can spin by dragging
 * (the project brief: skin changer with a 3D preview).
 *
 * A Minecraft skin is a flat 64 x 64 picture laid out like a paper model: for every body part
 * (head, body, arms, legs) there's a top, bottom, and four sides. This class builds a box for each
 * part and "wraps" the right piece of the skin around it – exactly how the game does it. A second,
 * slightly bigger layer on top holds the hat, jacket, sleeves and trousers.
 */
public class SkinModel3D extends StackPane {

    private final Group model = new Group();
    private final Rotate spin = new Rotate(-25, Rotate.Y_AXIS);
    private final Rotate tilt = new Rotate(-8, Rotate.X_AXIS);
    private double dragX;
    private double dragY;
    private PixelReader reader; // the current skin, so empty outer layers can be skipped

    public SkinModel3D(double width, double height) {
        getStyleClass().add("skin-preview");
        model.getTransforms().addAll(tilt, spin);

        // Lighting: a soft overall light plus a brighter "key" light from the front-left and above,
        // so the sides of every box are a little darker than the front – that's what makes the arms
        // stand out from the body (with flat light everything looked like one blob).
        Group world = new Group(model, new AmbientLight(Color.gray(0.52)));
        PointLight key = new PointLight(Color.gray(0.62));
        key.setTranslateX(-60);
        key.setTranslateY(-70);
        key.setTranslateZ(-90);
        PointLight rim = new PointLight(Color.web("#3AA8FF").deriveColor(0, 1, 0.35, 1)); // faint blue from behind
        rim.setTranslateX(70);
        rim.setTranslateY(-30);
        rim.setTranslateZ(80);
        world.getChildren().addAll(key, rim);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-78);
        camera.setTranslateY(-16);
        camera.setFieldOfView(35);
        camera.setFarClip(500);

        SubScene scene = new SubScene(world, width, height, true, SceneAntialiasing.BALANCED);
        scene.setCamera(camera);
        scene.setFill(Color.TRANSPARENT);
        getChildren().add(scene);
        setMaxSize(width, height);

        // Drag to spin the player around (and tilt a little).
        setOnMousePressed(event -> {
            dragX = event.getSceneX();
            dragY = event.getSceneY();
        });
        setOnMouseDragged(event -> {
            spin.setAngle(spin.getAngle() - (event.getSceneX() - dragX) * 0.8);
            tilt.setAngle(Math.max(-40, Math.min(40, tilt.getAngle() + (event.getSceneY() - dragY) * 0.4)));
            dragX = event.getSceneX();
            dragY = event.getSceneY();
        });
        setStyle("-fx-cursor: open-hand;");

        // A slow idle sway, like a character on a display stand. It pauses while the player drags,
        // and only runs while the preview is actually on screen (so it costs nothing elsewhere).
        javafx.animation.Timeline sway = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3.2),
                        new javafx.animation.KeyValue(spin.angleProperty(), 20, javafx.animation.Interpolator.EASE_BOTH)));
        sway.setAutoReverse(true);
        sway.setCycleCount(javafx.animation.Animation.INDEFINITE);
        setOnMouseReleased(event -> {
            if (Animations.enabled) {
                sway.play();
            }
        });
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> sway.pause());
        sceneProperty().addListener((o, oldScene, newScene) -> {
            if (newScene != null && Animations.enabled) {
                sway.play();
            } else {
                sway.stop();
            }
        });
    }

    /**
     * Shows a skin.
     *
     * @param skin a 64 x 64 (or old-style 64 x 32) skin picture
     * @param slim true for the "Alex" model with thinner (3-pixel) arms
     */
    public void setSkin(Image skin, boolean slim) {
        model.getChildren().clear();
        if (skin == null || skin.getWidth() < 64) {
            return;
        }
        boolean legacy = skin.getHeight() < 64; // old 64 x 32 skins have no separate left arm/leg or layers
        reader = skin.getPixelReader();
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(sharpen(skin));
        material.setSpecularColor(Color.BLACK); // no shiny highlights – Minecraft is matte

        double texW = 64;
        double texH = legacy ? 32 : 64;
        int arm = slim ? 3 : 4;

        // Each part: texture position (u, v), size (w, h, d), and where it sits (x, y, z of its top-left-front corner).
        // One unit = one skin pixel. Feet at y = 0, up is negative y (JavaFX's y points down).
        addPart(material, texW, texH, 0, 0, 8, 8, 8, -4, -32, -4, 0);                 // head
        addPart(material, texW, texH, 16, 16, 8, 12, 4, -4, -24, -2, 0);              // body
        addPart(material, texW, texH, 40, 16, arm, 12, 4, -4 - arm, -24, -2, 0);      // right arm
        addPart(material, texW, texH, legacy ? 40 : 32, legacy ? 16 : 48, arm, 12, 4, 4, -24, -2, 0); // left arm
        addPart(material, texW, texH, 0, 16, 4, 12, 4, -4, -12, -2, 0);               // right leg
        addPart(material, texW, texH, legacy ? 0 : 16, legacy ? 16 : 48, 4, 12, 4, 0, -12, -2, 0); // left leg

        // The outer layer, slightly bigger so it sits on top.
        addPart(material, texW, texH, 32, 0, 8, 8, 8, -4, -32, -4, 0.5);             // hat
        if (!legacy) {
            addPart(material, texW, texH, 16, 32, 8, 12, 4, -4, -24, -2, 0.25);      // jacket
            addPart(material, texW, texH, 40, 32, arm, 12, 4, -4 - arm, -24, -2, 0.25); // right sleeve
            addPart(material, texW, texH, 48, 48, arm, 12, 4, 4, -24, -2, 0.25);     // left sleeve
            addPart(material, texW, texH, 0, 32, 4, 12, 4, -4, -12, -2, 0.25);       // right trouser leg
            addPart(material, texW, texH, 0, 48, 4, 12, 4, 0, -12, -2, 0.25);        // left trouser leg
        }
    }

    /**
     * Adds one box with its piece of the skin wrapped around it.
     *
     * @param inflate how much bigger than normal (for the outer layer), in pixels
     */
    private void addPart(PhongMaterial material, double texW, double texH, double u, double v,
                         double w, double h, double d, double x, double y, double z, double inflate) {
        if (inflate > 0 && isEmpty(u, v, 2 * (w + d), d + h)) {
            return; // this outer layer (hat, jacket…) is see-through everywhere – nothing to draw
        }
        double x0 = x - inflate, x1 = x + w + inflate;
        double y0 = y - inflate, y1 = y + h + inflate;
        double z0 = z - inflate, z1 = z + d + inflate;

        // The 8 corners of the box ("front" faces the camera).
        Point3D p0 = new Point3D(x0, y0, z0), p1 = new Point3D(x1, y0, z0);
        Point3D p2 = new Point3D(x1, y1, z0), p3 = new Point3D(x0, y1, z0);
        Point3D p4 = new Point3D(x0, y0, z1), p5 = new Point3D(x1, y0, z1);
        Point3D p6 = new Point3D(x1, y1, z1), p7 = new Point3D(x0, y1, z1);

        TriangleMesh mesh = new TriangleMesh();
        // Each face: its 4 corners (top-left, top-right, bottom-right, bottom-left as seen from outside),
        // and where its picture is in the skin (u, v, width, height). This is Minecraft's skin layout.
        addFace(mesh, texW, texH, p0, p1, p2, p3, u + d, v + d, w, h);             // front
        addFace(mesh, texW, texH, p4, p0, p3, p7, u, v + d, d, h);                 // right side
        addFace(mesh, texW, texH, p1, p5, p6, p2, u + d + w, v + d, d, h);         // left side
        addFace(mesh, texW, texH, p5, p4, p7, p6, u + d + w + d, v + d, w, h);     // back
        addFace(mesh, texW, texH, p4, p5, p1, p0, u + d, v, w, d);                 // top
        addFace(mesh, texW, texH, p3, p2, p6, p7, u + d + w, v, w, d);             // bottom

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE); // draw both sides of every face
        model.getChildren().add(view);
    }

    /** True if every pixel in that area of the skin is fully see-through. */
    private boolean isEmpty(double u, double v, double w, double h) {
        for (int y = (int) v; y < v + h && y < 64; y++) {
            for (int x = (int) u; x < u + w && x < 64; x++) {
                if ((reader.getArgb(x, y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Adds one square face (two triangles) with its part of the skin picture. */
    private static void addFace(TriangleMesh mesh, double texW, double texH, Point3D tl, Point3D tr, Point3D br,
                                Point3D bl, double u, double v, double w, double h) {
        int p = mesh.getPoints().size() / 3;
        int t = mesh.getTexCoords().size() / 2;
        for (Point3D point : new Point3D[] {tl, tr, br, bl}) {
            mesh.getPoints().addAll((float) point.getX(), (float) point.getY(), (float) point.getZ());
        }
        // Texture positions go from 0 to 1 across the whole skin picture.
        mesh.getTexCoords().addAll(
                (float) (u / texW), (float) (v / texH),
                (float) ((u + w) / texW), (float) (v / texH),
                (float) ((u + w) / texW), (float) ((v + h) / texH),
                (float) (u / texW), (float) ((v + h) / texH));
        mesh.getFaces().addAll(p, t, p + 1, t + 1, p + 2, t + 2);
        mesh.getFaces().addAll(p, t, p + 2, t + 2, p + 3, t + 3);
    }

    /**
     * 3D pictures get smoothed, which makes pixel art blurry. Blowing the skin up 8 times first
     * (each pixel becomes an 8 x 8 block) keeps the edges crisp.
     */
    private static Image sharpen(Image skin) {
        int scale = 8;
        int w = (int) skin.getWidth();
        int h = (int) skin.getHeight();
        WritableImage big = new WritableImage(w * scale, h * scale);
        PixelReader reader = skin.getPixelReader();
        PixelWriter writer = big.getPixelWriter();
        for (int y = 0; y < h * scale; y++) {
            for (int x = 0; x < w * scale; x++) {
                writer.setArgb(x, y, reader.getArgb(x / scale, y / scale));
            }
        }
        return big;
    }
}
