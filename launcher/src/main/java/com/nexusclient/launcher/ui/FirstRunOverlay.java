package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * FirstRunOverlay – the screens a player sees the very first time they open Nexus
 * (the project brief, Launcher → First run):
 *
 *   1. Welcome – the Nexus logo with a short animation
 *   2. Tutorial – 5 quick steps
 *   3. Microsoft sign-in (can be skipped)
 *   4. TorcherRig – or, if they say no, the manual memory (RAM) prompt
 *
 * If they skip both TorcherRig and the RAM prompt, Nexus asks again after they sign in.
 * The overlay covers the whole window, and fades away when it's finished.
 */
public class FirstRunOverlay extends StackPane {

    /** Where to start. The "ask again after sign-in" case starts straight at OPTIMISE. */
    public enum Start { WELCOME, OPTIMISE }

    /** One tutorial step. The 5 steps were chosen as defaults – the owner can change them here. */
    private record TutorialStep(String icon, String title, String text) {}

    private static final List<TutorialStep> TUTORIAL = List.of(
            new TutorialStep(Icons.HOME, "Welcome to Nexus",
                    "Nexus is a Minecraft client made to look good and run fast. This quick tour shows you "
                            + "around – it takes less than a minute."),
            new TutorialStep(Icons.VERSION, "Instances",
                    "An instance is its own Minecraft setup, with its own version, worlds and settings. "
                            + "Pick one on the left, choose its version in the Launch bar, and press LAUNCH."),
            new TutorialStep(Icons.MODS, "Mods & performance",
                    "Add mods, resource packs and shaders in the Mods tab. TorcherRig tunes Nexus to your PC, "
                            + "and DFR makes the game look smoother."),
            new TutorialStep(Icons.ERROR_SCAN, "If the game crashes",
                    "The Errors tab finds out why, explains it in plain English and offers to fix it – "
                            + "with an Undo button, just in case."),
            new TutorialStep(Icons.FRIENDS, "Play together",
                    "Add friends, start parties, join a clan and earn honor for it just by playing."));

    private final AccountMenu accountMenu;
    private final InstanceStore store;
    private final InstancePanel instancePanel;
    private final Runnable onClose;

    /** The card in the middle whose contents change from screen to screen. */
    private final VBox card = new VBox(16);

    /**
     * @param onClose called after the overlay has faded away
     */
    public FirstRunOverlay(Start start, AccountMenu accountMenu, InstanceStore store,
                           InstancePanel instancePanel, Runnable onClose) {
        this.accountMenu = accountMenu;
        this.store = store;
        this.instancePanel = instancePanel;
        this.onClose = onClose;

        getStyleClass().add("firstrun-backdrop");
        card.getStyleClass().add("firstrun-card");
        card.setMaxWidth(680);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(card);

        if (start == Start.WELCOME) {
            showWelcome();
        } else {
            showOptimise();
        }
    }

    // ================================================================== 1. welcome

    private void showWelcome() {
        // The logo: each letter of NEXUS rises and fades in, one after another.
        HBox letters = new HBox(4);
        letters.setAlignment(Pos.CENTER);
        SequentialTransition intro = new SequentialTransition();
        for (char c : "NEXUS".toCharArray()) {
            Label letter = new Label(String.valueOf(c));
            letter.getStyleClass().add("welcome-letter");
            letter.setOpacity(0);
            letter.setTranslateY(18);
            letters.getChildren().add(letter);

            FadeTransition fade = new FadeTransition(Duration.millis(260), letter);
            fade.setToValue(1);
            TranslateTransition rise = new TranslateTransition(Duration.millis(260), letter);
            rise.setToY(0);
            intro.getChildren().add(new ParallelTransition(fade, rise));
        }

        Label client = new Label("C L I E N T");
        client.getStyleClass().add("welcome-sub");
        Label tagline = new Label("The neon Minecraft client. Let's get you set up.");
        tagline.getStyleClass().add("torcher-text");

        Button start = new Button("Get started");
        start.getStyleClass().add("primary-button");
        start.setOnAction(event -> showTutorial(0));

        VBox after = new VBox(14, client, tagline, start);
        after.setAlignment(Pos.CENTER);
        after.setOpacity(0);
        FadeTransition showRest = new FadeTransition(Duration.millis(400), after);
        showRest.setToValue(1);
        intro.getChildren().addAll(new PauseTransition(Duration.millis(150)), showRest);

        VBox welcome = new VBox(10, letters, after);
        welcome.setAlignment(Pos.CENTER);
        setCard(welcome);
        if (Animations.enabled) {
            intro.play();
        } else {
            letters.getChildren().forEach(node -> { node.setOpacity(1); node.setTranslateY(0); });
            after.setOpacity(1);
        }
    }

    // ================================================================== 2. tutorial

    /** Shows one tutorial step (public so the developer tour can visit it). */
    public void showTutorial(int index) {
        TutorialStep step = TUTORIAL.get(index);

        // Progress dots: one per step, the current one lit up.
        HBox dots = new HBox(8);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < TUTORIAL.size(); i++) {
            Region dot = new Region();
            dot.getStyleClass().add("tutorial-dot");
            if (i == index) {
                dot.getStyleClass().add("active");
            }
            dots.getChildren().add(dot);
        }

        Node icon = Icons.create(step.icon(), 44);
        icon.getStyleClass().add("tutorial-icon");
        Label count = new Label("STEP " + (index + 1) + " OF " + TUTORIAL.size());
        count.getStyleClass().add("banner-eyebrow");
        Label title = new Label(step.title());
        title.getStyleClass().add("torcher-title");
        Label text = new Label(step.text());
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);

        Button back = new Button("Back");
        back.getStyleClass().add("secondary-button");
        back.setDisable(index == 0);
        back.setOnAction(event -> showTutorial(index - 1));
        boolean last = index == TUTORIAL.size() - 1;
        Button next = new Button(last ? "Finish tour" : "Next");
        next.getStyleClass().add("primary-button");
        next.setOnAction(event -> {
            if (last) {
                showLogin();
            } else {
                showTutorial(index + 1);
            }
        });
        Hyperlink skip = new Hyperlink("Skip tour");
        skip.getStyleClass().add("retry-link");
        skip.setOnAction(event -> showLogin());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, skip, spacer, back, next);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14, icon, count, title, text, dots, buttons);
        setCard(content);
    }

    // ================================================================== 3. sign-in

    public void showLogin() {
        Node icon = Icons.create(Icons.SKINS, 44);
        icon.getStyleClass().add("tutorial-icon");
        Label title = new Label("Sign in with Microsoft");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("Sign in with the Microsoft account you play Minecraft with. Your browser opens "
                + "Microsoft's own login page – Nexus never sees your password. You can add up to 3 accounts.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);

        Button signIn = new Button("Sign in with Microsoft");
        signIn.getStyleClass().add("primary-button");
        signIn.setOnAction(event -> {
            accountMenu.startSignIn(); // finishes in the background; a message pops up when done
            showOptimise();
        });
        Button skip = new Button("Skip for now");
        skip.getStyleClass().add("secondary-button");
        skip.setOnAction(event -> showOptimise());

        setCard(new VBox(14, icon, title, text, new HBox(10, signIn, skip)));
    }

    // ================================================================== 4. TorcherRig / RAM

    public void showOptimise() {
        TorcherRigPane torcherRig = new TorcherRigPane(TorcherRigPane.Mode.FIRST_RUN, store, instancePanel,
                new TorcherRigPane.Listener() {
                    @Override
                    public void done() {
                        finish();
                    }

                    @Override
                    public void declined() {
                        showRamPrompt();
                    }
                });
        setCard(torcherRig);
    }

    public void showRamPrompt() {
        RamPromptPane ram = new RamPromptPane(this::finish, () -> {
            // Skipped TorcherRig AND the memory prompt: ask again after they sign in.
            Settings settings = SettingsStore.get();
            settings.optimisePromptPending = true;
            SettingsStore.save();
            finish();
        });
        setCard(ram);
    }

    // ================================================================== helpers

    /** Swaps the card's contents with a quick fade. */
    private void setCard(Node content) {
        card.getChildren().setAll(content);
        if (Animations.enabled) {
            content.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(220), content);
            fade.setToValue(1);
            fade.play();
        }
    }

    /** Marks the first run as done and fades the overlay away. */
    private void finish() {
        Settings settings = SettingsStore.get();
        settings.firstRunComplete = true;
        SettingsStore.save();
        FadeTransition fade = new FadeTransition(Duration.millis(Animations.enabled ? 300 : 1), this);
        fade.setToValue(0);
        fade.setOnFinished(event -> onClose.run());
        fade.play();
    }
}
