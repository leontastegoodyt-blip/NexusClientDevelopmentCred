package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.i18n.Lang;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.integrations.DiscordPresence;
import com.nexusclient.launcher.integrations.Updater;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/**
 * SettingsPage – the Settings tab:
 *
 *   General       – what the launcher does when the game starts, Discord status, updates
 *   Appearance    – accent colour and background picture
 *   Game          – memory (RAM)
 *   TorcherRig    – run it again, or undo it
 *   Accessibility – reduced motion, bigger text, colourblind modes
 *   Language      – the launcher's language
 *   About         – version, where files are kept, credits
 *
 * Changes save straight away and take effect immediately (see Theme).
 */
public class SettingsPage extends VBox {

    private final InstanceStore store;
    private final InstancePanel instancePanel;
    private final VBox body = new VBox(16);
    private TabStrip tabs;

    public SettingsPage(InstanceStore store, InstancePanel instancePanel) {
        this.store = store;
        this.instancePanel = instancePanel;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.SETTINGS.title());
        title.getStyleClass().add("page-title");
        HBox header = new HBox(PageHeader.block(Page.SETTINGS, title));
        header.setAlignment(Pos.CENTER_LEFT);

        tabs = new TabStrip(Page.SETTINGS.tabs(), this::showTab);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, tabs, scroll);
        showTab(Page.SETTINGS.tabs().get(0));
    }

    /** Shows one sub-tab (public so the developer tour can visit it). */
    public void showTab(String tab) {
        if (tabs != null) {
            tabs.select(tab); // keep the highlighted pill in step
        }
        Node content = switch (tab) {
            case "General" -> card(general());
            case "Appearance" -> card(appearance());
            case "Game" -> card(new RamPromptPane(null, null));
            case "TorcherRig" -> card(new TorcherRigPane(TorcherRigPane.Mode.SETTINGS, store, instancePanel, () -> {}));
            case "Accessibility" -> card(accessibility());
            case "Language" -> card(language());
            case "Premium" -> card(premium());
            default -> card(about());
        };
        body.getChildren().setAll(content);
        Animations.pageIn(content);
    }

    // ================================================================== Premium

    /**
     * Nexus Premium: your status (bought, or free while your clan is in the top 3) and the perks.
     * Buying isn't available yet – the price and payment provider are still to be decided.
     */
    private Node premium() {
        Label title = new Label("Nexus Premium");
        title.getStyleClass().add("section-title");
        Label status = new Label("Checking your Premium status…");
        status.getStyleClass().add("torcher-title");
        status.setWrapText(true);
        Label perks = new Label("Perks:\n"
                + "  • Own and be in 2 clans instead of 1\n"
                + "  • More perks are coming\n\n"
                + "Members of the top 3 clans on the honor leaderboard get Premium free, for as long as their clan "
                + "stays on the podium.");
        perks.getStyleClass().add("torcher-text");
        perks.setWrapText(true);
        javafx.scene.control.Button buy = new javafx.scene.control.Button("Get Premium – coming soon");
        buy.getStyleClass().add("primary-button");
        buy.setDisable(true);

        com.nexusclient.launcher.online.NexusOnline online = com.nexusclient.launcher.online.NexusOnline.get();
        Background.run("premium", () -> online.get("/api/me").getAsJsonObject(), me -> {
            if (!me.get("premium").getAsBoolean()) {
                status.setText("You don't have Premium yet.");
            } else if (me.get("premiumFromPodium").getAsBoolean()) {
                status.setText("★ You have Premium – free, because your clan is in the top 3!");
            } else {
                status.setText("★ You have Premium until " + me.get("premiumUntil").getAsString().substring(0, 10) + ".");
            }
        }, error -> status.setText(online.signedIn() ? "Couldn't check: " + error.getMessage()
                : "Sign in to Nexus to see your status."));
        return new VBox(12, title, status, perks, buy);
    }

    // ================================================================== General

    private Node general() {
        Settings settings = SettingsStore.get();

        // --- When the game starts ---
        ToggleGroup behaviour = new ToggleGroup();
        VBox behaviourChoices = new VBox(8);
        for (String[] choice : new String[][] {
                {"STAY_OPEN", "Stay open"},
                {"MINIMISE_TO_TRAY", "Minimise to tray"},
                {"HIDE", "Hide until the game closes"}}) {
            RadioButton option = new RadioButton(choice[1]);
            option.setToggleGroup(behaviour);
            option.setSelected(choice[0].equals(settings.launchBehaviour));
            option.setOnAction(event -> {
                settings.launchBehaviour = choice[0];
                SettingsStore.save();
            });
            behaviourChoices.getChildren().add(option);
        }

        // --- Discord ---
        CheckBox discord = new CheckBox("Show \"Playing on Nexus Client\" on my Discord profile");
        discord.setSelected(settings.discordPresence);
        discord.setOnAction(event -> {
            settings.discordPresence = discord.isSelected();
            SettingsStore.save();
        });
        Label discordNote = hint(DiscordPresence.isConfigured() ? "Works while the Discord app is open."
                : "Coming soon.");

        // --- Updates ---
        CheckBox updates = new CheckBox("Check for updates on start");
        updates.setSelected(settings.checkForUpdates);
        updates.setOnAction(event -> {
            settings.checkForUpdates = updates.isSelected();
            SettingsStore.save();
        });
        Button checkNow = new Button("Check now");
        checkNow.getStyleClass().add("secondary-button");
        checkNow.setOnAction(event -> checkForUpdates(true));

        // --- Crash reports (opt-in) ---
        CheckBox crashReports = new CheckBox("Send crash reports to Nexus automatically");
        crashReports.setSelected(settings.sendCrashReports);
        crashReports.setOnAction(event -> {
            settings.sendCrashReports = crashReports.isSelected();
            SettingsStore.save();
        });

        return new VBox(12,
                heading("When the game starts"), behaviourChoices,
                heading("Discord"), discord, discordNote,
                heading("Updates"), updates, checkNow,
                hint("You're on Nexus " + Updater.CURRENT_VERSION + "."),
                heading("Crash reports"), crashReports,
                hint("PC details, mod list and the log ending. Never passwords."));
    }

    /**
     * Checks for a Nexus update and, if there is one, asks whether to get it.
     *
     * @param tellIfNone show a message even when there's no update (when the player asked)
     */
    public static void checkForUpdates(boolean tellIfNone) {
        if (!Updater.isConfigured()) {
            if (tellIfNone) {
                Toast.info("Updates aren't set up yet – they will be once the Nexus website is online.");
            }
            return;
        }
        Background.run("update-check", Updater::check, update -> {
            if (update == null) {
                if (tellIfNone) {
                    Toast.info("You're on the newest Nexus (" + Updater.CURRENT_VERSION + ").");
                }
                return;
            }
            Modal.confirm("Nexus " + update.version() + " is available",
                    (update.notes() == null ? "" : update.notes() + "\n\n")
                            + "Download it now? Your browser will open the download.",
                    "Download", () -> {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI.create(update.download()));
                        } catch (Exception e) {
                            Toast.error("Couldn't open the download: " + e.getMessage());
                        }
                    });
        }, error -> {
            if (tellIfNone) {
                Toast.error("Couldn't check for updates: " + error.getMessage());
            }
        });
    }

    // ================================================================== Appearance

    private Node appearance() {
        Settings settings = SettingsStore.get();

        ColorPicker accent = new ColorPicker(Color.web(settings.accentColor != null ? settings.accentColor : "#3AA8FF"));
        accent.getStyleClass().add("form-combo");
        accent.setOnAction(event -> {
            settings.accentColor = toHex(accent.getValue());
            SettingsStore.save();
            Theme.apply();
        });
        Button resetAccent = new Button("Reset to Nexus blue");
        resetAccent.getStyleClass().add("secondary-button");
        resetAccent.setOnAction(event -> {
            settings.accentColor = null;
            SettingsStore.save();
            Theme.apply();
            accent.setValue(Color.web("#3AA8FF"));
        });

        Button chooseBackground = new Button("Choose a picture…");
        chooseBackground.getStyleClass().add("secondary-button");
        chooseBackground.setOnAction(event -> chooseBackground());
        Button removeBackground = new Button("Remove");
        removeBackground.getStyleClass().add("secondary-button");
        removeBackground.setOnAction(event -> {
            settings.backgroundImage = null;
            SettingsStore.save();
            Theme.apply();
        });

        // Interface size: how big the whole launcher is drawn (sharp at any size). Needs a restart,
        // because it's chosen before the window opens (see DisplaySetup).
        ComboBox<Integer> size = new ComboBox<>();
        size.getStyleClass().add("form-combo");
        for (int choice : DisplaySetup.SIZE_CHOICES) {
            size.getItems().add(choice);
        }
        size.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer percent) {
                if (percent == null) {
                    return "";
                }
                return percent == 0 ? "Automatic (" + DisplaySetup.automaticPercent() + "% on this screen)" : percent + "%";
            }

            @Override
            public Integer fromString(String text) {
                return null; // the list can't be typed into
            }
        });
        size.setValue(settings.uiScalePercent);
        size.setOnAction(event -> {
            settings.uiScalePercent = size.getValue();
            SettingsStore.save();
            Toast.info("Restart Nexus to use the new interface size.");
        });

        return new VBox(12,
                heading("Accent colour"), hint("Highlights, buttons and glows."),
                new HBox(10, accent, resetAccent),
                heading("Interface size"), hint("Automatic = bigger on 1440p and 4K. Needs a restart."),
                size,
                heading("Background picture"), hint("Shown faintly behind the pages."),
                new HBox(10, chooseBackground, removeBackground));
    }

    private void chooseBackground() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a background picture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            // Copy it into the Nexus folder, so it still works if the original is moved or deleted.
            Path dir = NexusPaths.root().resolve("backgrounds");
            Files.createDirectories(dir);
            Path copy = dir.resolve(file.getName());
            Files.copy(file.toPath(), copy, StandardCopyOption.REPLACE_EXISTING);
            SettingsStore.get().backgroundImage = copy.toString();
            SettingsStore.save();
            Theme.apply();
        } catch (Exception e) {
            Toast.error("Couldn't use that picture: " + e.getMessage());
        }
    }

    private static String toHex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
    }

    // ================================================================== Accessibility

    private Node accessibility() {
        Settings settings = SettingsStore.get();

        CheckBox reducedMotion = new CheckBox("Reduced motion – turn off animations");
        reducedMotion.setSelected(settings.reducedMotion);
        reducedMotion.setOnAction(event -> {
            settings.reducedMotion = reducedMotion.isSelected();
            SettingsStore.save();
            Theme.apply();
        });
        CheckBox biggerText = new CheckBox("Bigger text");
        biggerText.setSelected(settings.biggerText);
        biggerText.setOnAction(event -> {
            settings.biggerText = biggerText.isSelected();
            SettingsStore.save();
            Theme.apply();
        });

        ToggleGroup colourblind = new ToggleGroup();
        VBox colourChoices = new VBox(8);
        for (String[] choice : new String[][] {
                {"OFF", "Off"},
                {"RED_GREEN", "Red–green (blue and orange instead)"},
                {"BLUE_YELLOW", "Blue–yellow (pink instead of blue)"}}) {
            RadioButton option = new RadioButton(choice[1]);
            option.setToggleGroup(colourblind);
            option.setSelected(choice[0].equals(settings.colourblindMode));
            option.setOnAction(event -> {
                settings.colourblindMode = choice[0];
                SettingsStore.save();
                Theme.apply();
            });
            colourChoices.getChildren().add(option);
        }

        return new VBox(12, heading("Motion and text"), reducedMotion, biggerText,
                heading("Colourblind mode"), colourChoices,
                hint("Also used in-game."));
    }

    // ================================================================== Language

    private Node language() {
        Settings settings = SettingsStore.get();
        ComboBox<String> picker = new ComboBox<>();
        picker.getStyleClass().add("form-combo");
        for (Map.Entry<String, String> language : Lang.LANGUAGES.entrySet()) {
            picker.getItems().add(language.getValue());
            if (language.getKey().equals(settings.language)) {
                picker.setValue(language.getValue());
            }
        }
        picker.setOnAction(event -> {
            for (Map.Entry<String, String> language : Lang.LANGUAGES.entrySet()) {
                if (language.getValue().equals(picker.getValue()) && !language.getKey().equals(settings.language)) {
                    settings.language = language.getKey();
                    SettingsStore.save();
                    Toast.info("Restart Nexus to switch to " + language.getValue() + ".");
                }
            }
        });
        return new VBox(12, heading("Language"), picker,
                hint("More languages coming."));
    }

    // ================================================================== About

    private static Node about() {
        Label name = new Label("Nexus Client");
        name.getStyleClass().add("torcher-title");
        Label version = new Label("Version " + Updater.CURRENT_VERSION + "  ·  development build");
        version.getStyleClass().add("torcher-text");
        Label folder = new Label("Nexus files are stored in: " + NexusPaths.root());
        folder.getStyleClass().add("torcher-text");
        folder.setWrapText(true);
        Label legal = new Label("Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.");
        legal.getStyleClass().add("torcher-tip");
        legal.setWrapText(true);
        Label credits = new Label("Icons: Google Material Icons (Apache License 2.0). Hardware detection: OSHI (MIT License). "
                + "JSON: Gson (Apache License 2.0). Native access: JNA (Apache License 2.0). "
                + "Mods, packs and shaders: Modrinth.");
        credits.getStyleClass().add("torcher-tip");
        credits.setWrapText(true);
        return new VBox(10, name, version, folder, legal, credits);
    }

    // ================================================================== small pieces

    /** Wraps a section in a rounded card. */
    private static Node card(Node content) {
        VBox card = new VBox(content);
        card.getStyleClass().add("settings-card");
        card.setMaxWidth(820);
        return card;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("preview-note");
        label.setWrapText(true);
        return label;
    }
}
