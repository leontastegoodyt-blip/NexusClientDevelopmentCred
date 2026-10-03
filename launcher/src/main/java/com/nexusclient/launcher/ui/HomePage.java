package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.account.LoginException;
import com.nexusclient.launcher.errorscan.Diagnosis;
import com.nexusclient.launcher.errorscan.ErrorScanner;
import com.nexusclient.launcher.game.GameLaunchTask;
import com.nexusclient.launcher.integrations.DiscordPresence;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.game.QuickPlay;
import com.nexusclient.launcher.servers.ServerStore;
import com.nexusclient.launcher.tools.Playtime;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.settings.SettingsStore;
import com.nexusclient.launcher.torcherrig.TorcherRig;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * HomePage – what the Home tab shows.
 *
 *   ┌───────────────────────────────────────────┬──────────────┐
 *   │ Welcome banner with stats                 │ Friends      │
 *   │ Latest news (3 cards)                     │              │
 *   │ Featured servers                          │ Party        │
 *   ├───────────────────────────────────────────┴──────────────┤
 *   │ Launch bar: selected instance, details, LAUNCH button    │
 *   └──────────────────────────────────────────────────────────┘
 */
public class HomePage extends BorderPane {

    /** One news card. The cards come from data/news.json inside Nexus (later: the Nexus website). */
    private record NewsItem(String tag, String title, String text) {}
    private record NewsFile(List<NewsItem> news) {}

    /** Reads the news cards from data/news.json. */
    private static List<NewsItem> loadNews() {
        try (java.io.InputStream in = HomePage.class.getResourceAsStream("/com/nexusclient/launcher/data/news.json")) {
            NewsFile file = new com.google.gson.Gson().fromJson(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), NewsFile.class);
            return file.news().subList(0, Math.min(3, file.news().size()));
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private final InstancePanel instancePanel;
    private final InstanceStore store;
    private final AccountManager accounts;
    private final ServerStore servers;

    // Parts of the Launch bar that change when a different instance is selected.
    private final StackPane launchBadge = new StackPane();
    private final Label instanceName = new Label();
    // The version dropdown. Its items are version choices: Instance.LATEST, "26.3", ..., "1.8.9".
    private final ComboBox<String> versionPicker = new ComboBox<>();
    private final Label loaderChip = chip(Icons.MODS, "Vanilla");
    private final Label ramChip = chip(Icons.MEMORY, "RAM: Auto");
    private final Label status = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final Button launchButton = new Button("LAUNCH");
    // "Quick Play": rejoin the last server, or continue the last world.
    private final Button quickPlayButton = new Button();
    private QuickPlay quickPlayTarget;

    // The total playtime shown in the banner (updated after each session).
    private final javafx.beans.property.SimpleStringProperty playtimeText =
            new javafx.beans.property.SimpleStringProperty(Playtime.format(Playtime.totalSeconds()));

    private Instance selected;

    /** The game launch in progress (downloading or playing), or null if none. */
    private GameLaunchTask runningLaunch;

    /**
     * True while the code itself is changing the dropdown, so that doesn't count as the player
     * picking a version.
     */
    private boolean updatingPicker = false;

    /**
     * @param instancePanel the Instances column. The Launch bar follows its selected instance,
     *                      and the banner shows how many instances there are.
     * @param store         where instances are saved – a version choice is saved here
     * @param accounts      the signed-in accounts – LAUNCH plays as the active one
     * @param servers       the server lists (featured servers are shown on Home)
     */
    public HomePage(InstancePanel instancePanel, InstanceStore store, AccountManager accounts, ServerStore servers) {
        this.instancePanel = instancePanel;
        this.store = store;
        this.accounts = accounts;
        this.servers = servers;
        getStyleClass().add("home-page");

        // --- The main column (scrolls if the window is short) ---
        VBox main = new VBox(28,
                buildBanner(instancePanel),
                buildNews(),
                buildFeaturedServers());
        main.getStyleClass().add("home-main");

        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");

        // --- Main column on the left, social column on the right ---
        HBox columns = new HBox(24, scroll, buildSocialColumn());
        HBox.setHgrow(scroll, Priority.ALWAYS);
        setCenter(columns);

        // --- Launch bar along the bottom ---
        HBox launchBar = buildLaunchBar();
        BorderPane.setMargin(launchBar, new Insets(20, 0, 0, 0));
        setBottom(launchBar);

        // Whenever a different instance is selected, update the Launch bar.
        instancePanel.selectedInstanceProperty().addListener(
                (observable, oldInstance, newInstance) -> showInstance(newInstance));
        showInstance(instancePanel.selectedInstanceProperty().get());
    }

    // ------------------------------------------------------------------ main column

    /** The welcome banner, with a row of stats along its bottom. */
    private VBox buildBanner(InstancePanel instancePanel) {
        Label eyebrow = new Label("NEXUS CLIENT  ·  DEV BUILD 0.1.0");
        eyebrow.getStyleClass().add("banner-eyebrow");
        Label title = new Label("Welcome back");
        title.getStyleClass().add("banner-title");
        Label text = new Label("Pick an instance on the left, choose its version below, and hit Launch.");
        text.getStyleClass().add("banner-text");

        // Stats. "bind" means the number updates by itself whenever the value changes.
        HBox stats = new HBox(40,
                stat("INSTANCES", Bindings.size(instancePanel.getInstances()).asString()),
                stat("LATEST MINECRAFT", instancePanel.latestVersionProperty()),
                stat("PLAYTIME", playtimeText),
                stat("FRIENDS ONLINE", null)); // friends arrive in build step 13
        stats.getStyleClass().add("banner-stats");

        VBox banner = new VBox(6, eyebrow, title, text, stats);
        banner.getStyleClass().add("home-banner");
        return banner;
    }

    /**
     * One stat: a small caption above a bigger value.
     * If there's no value yet (null), it shows a dash.
     */
    private VBox stat(String caption, ObservableValue<String> value) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");
        Label valueLabel = new Label("—");
        valueLabel.getStyleClass().add("stat-value");
        if (value != null) {
            valueLabel.textProperty().bind(value);
        }
        return new VBox(2, captionLabel, valueLabel);
    }

    /** "Latest news" heading and a row of cards. */
    private VBox buildNews() {
        HBox cards = new HBox(16);
        for (NewsItem item : loadNews()) {
            Label tag = new Label(item.tag());
            tag.getStyleClass().add("news-tag");
            Label title = new Label(item.title());
            title.getStyleClass().add("news-title");
            title.setWrapText(true); // long titles wrap instead of being cut off
            Label text = new Label(item.text());
            text.getStyleClass().add("news-text");
            text.setWrapText(true);

            VBox card = new VBox(6, tag, title, text);
            card.getStyleClass().add("news-card");
            // Give every card the same starting width, then let them all grow by the same amount,
            // so the cards are always equally wide no matter how much text each one has.
            card.setPrefWidth(200);
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            cards.getChildren().add(card);
        }
        return new VBox(12, sectionHeader("Latest news", null), cards);
    }

    /** The first few featured servers, with live player counts and one-click join. */
    private VBox buildFeaturedServers() {
        VBox rows = new VBox(10);
        for (ServerStore.Server server : servers.featured().subList(0, Math.min(3, servers.featured().size()))) {
            rows.getChildren().add(new ServerCard(server, servers, this::joinServer));
        }
        VBox section = new VBox(12, sectionHeader("Featured servers", "More in the Servers tab"), rows);
        // Nexus Partner Servers get their own section above (once they've loaded from the Nexus server).
        VBox partnerRows = new VBox(10);
        VBox partnerSection = new VBox(12, sectionHeader("Nexus Partner Servers", "Partner"), partnerRows);
        Runnable showPartners = () -> {
            partnerRows.getChildren().clear();
            for (ServerStore.Server server : servers.partners().subList(0, Math.min(3, servers.partners().size()))) {
                partnerRows.getChildren().add(new ServerCard(server, servers, this::joinServer));
            }
            boolean any = !servers.partners().isEmpty();
            if (any && !section.getChildren().contains(partnerSection)) {
                section.getChildren().add(0, partnerSection);
            } else if (!any) {
                section.getChildren().remove(partnerSection);
            }
        };
        servers.partners().addListener((javafx.collections.ListChangeListener<ServerStore.Server>) change -> showPartners.run());
        showPartners.run();
        return section;
    }

    /** A section heading, with an optional small pill on the right. */
    private HBox sectionHeader(String title, String pill) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        HBox header = new HBox(heading);
        header.setAlignment(Pos.CENTER_LEFT);
        if (pill != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label pillLabel = new Label(pill);
            pillLabel.getStyleClass().add("step-pill");
            header.getChildren().addAll(spacer, pillLabel);
        }
        return header;
    }

    // ------------------------------------------------------------------ social column

    /** The column on the right: friends list and party. */
    private VBox buildSocialColumn() {
        VBox friends = socialCard("FRIENDS", "0 online", Icons.FRIENDS,
                "Sign in to see your friends",
                "See who's online and which server they're on. Arrives in build step 13.");
        VBox.setVgrow(friends, Priority.ALWAYS); // friends take the spare height

        VBox party = socialCard("PARTY", null, Icons.CLANS,
                "You're not in a party",
                "Invite friends and join servers together.");

        VBox column = new VBox(16, friends, party);
        column.getStyleClass().add("social-column");
        return column;
    }

    /** A card with a small heading and an "empty" message in the middle. */
    private VBox socialCard(String title, String count, String icon, String message, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        HBox header = new HBox(8, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        if (count != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label countLabel = new Label(count);
            countLabel.getStyleClass().add("social-count");
            header.getChildren().addAll(spacer, countLabel);
        }

        Node iconNode = Icons.create(icon, 30);
        iconNode.getStyleClass().add("empty-icon");
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("empty-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("empty-text");
        hintLabel.setWrapText(true);

        VBox empty = new VBox(8, iconNode, messageLabel, hintLabel);
        empty.setAlignment(Pos.CENTER);
        VBox.setVgrow(empty, Priority.ALWAYS);

        VBox card = new VBox(12, header, empty);
        card.getStyleClass().add("social-card");
        return card;
    }

    // ------------------------------------------------------------------ launch bar

    /**
     * The bar at the bottom:
     * [badge] name + detail chips  ......  status  [settings] [LAUNCH]
     */
    private HBox buildLaunchBar() {
        instanceName.getStyleClass().add("launch-instance");

        // Small "chips" with details about the selected instance (filled in by showInstance).
        quickPlayButton.getStyleClass().add("quick-play-button");
        quickPlayButton.setGraphic(Icons.create(Icons.PLAY, 11));
        quickPlayButton.setOnAction(event -> launch(quickPlayTarget, false));
        quickPlayButton.managedProperty().bind(quickPlayButton.visibleProperty());
        HBox chips = new HBox(6, buildVersionPicker(), loaderChip, ramChip, quickPlayButton);
        chips.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(6, instanceName, chips);
        info.setMinWidth(Region.USE_PREF_SIZE); // never squash the chips – the status text wraps instead

        // Status message with a thin progress bar under it (the bar only shows while launching).
        status.getStyleClass().add("launch-status");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setAlignment(Pos.CENTER_RIGHT);
        progressBar.getStyleClass().add("launch-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        VBox statusBox = new VBox(8, status, progressBar);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(statusBox, Priority.ALWAYS);

        Button settings = new Button();
        settings.setGraphic(Icons.create(Icons.SETTINGS, 20));
        settings.getStyleClass().add("launch-settings-button");
        settings.setTooltip(new Tooltip("Instance settings"));
        settings.setOnAction(event -> instancePanel.openSettings(selected));

        launchButton.setGraphic(Icons.create(Icons.PLAY, 22));
        launchButton.getStyleClass().add("launch-button");
        // The same button is LAUNCH normally, and STOP while a game is launching or running.
        launchButton.setOnAction(event -> {
            if (runningLaunch != null) {
                runningLaunch.stop();
            } else {
                launch(null, false);
            }
        });

        HBox bar = new HBox(16, launchBadge, info, statusBox, settings, buildLaunchWithGlow());
        bar.getStyleClass().add("launch-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * The LAUNCH button with a soft neon glow behind it that slowly "breathes" (fades in and out).
     * The glow is a blurred copy of the button's colours, placed underneath it in a StackPane.
     * It hides while LAUNCH is switched off or turned into STOP.
     */
    private StackPane buildLaunchWithGlow() {
        Region glow = new Region();
        glow.getStyleClass().add("launch-glow");
        glow.setMouseTransparent(true); // clicks go to the button, not the glow
        glow.setEffect(new GaussianBlur(22)); // soften it into a glow that spills past the button
        glow.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !launchButton.isDisabled() && !launchButton.getStyleClass().contains("stop-mode"),
                launchButton.disabledProperty(), launchButton.getStyleClass()));
        Animations.breathe(glow, 0.25, 0.7, Duration.seconds(1.8));
        return new StackPane(glow, launchButton);
    }

    /** A small rounded label with an icon, used for instance details. */
    private static Label chip(String icon, String text) {
        Label chip = new Label(text, Icons.create(icon, 12));
        chip.getStyleClass().add("chip");
        return chip;
    }

    // ------------------------------------------------------------------ version picker

    /**
     * The version dropdown in the Launch bar. Picking a version saves it to the instance straight
     * away (forever, until the player picks another one). "Latest" always follows the newest release.
     */
    private ComboBox<String> buildVersionPicker() {
        versionPicker.getStyleClass().add("version-combo");
        versionPicker.setPromptText("Choose a version");
        versionPicker.setTooltip(new Tooltip("Which Minecraft version this instance plays"));

        // How each choice is written: "Latest (26.3)" or "1.8.9".
        versionPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(String choice) {
                return choice == null ? "" : instancePanel.describeVersion(choice);
            }

            @Override
            public String fromString(String text) {
                return null; // only needed for typing into the box, which we don't allow
            }
        });
        // The closed dropdown shows "Choose a version" when nothing is picked. (JavaFX sometimes
        // forgets to show the prompt text after the value is cleared, so we draw it ourselves.)
        versionPicker.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? "Choose a version" : instancePanel.describeVersion(choice));
            }
        });

        // Fill the dropdown whenever the version list arrives from Mojang.
        instancePanel.getVersions().addListener((ListChangeListener<MinecraftVersion>) change -> refreshPickerItems());
        refreshPickerItems();

        // When the player picks a version, save it to the selected instance.
        versionPicker.valueProperty().addListener((observable, oldChoice, newChoice) -> {
            if (updatingPicker || selected == null || newChoice == null || newChoice.equals(selected.version())) {
                return;
            }
            Instance updated = store.setVersion(selected, newChoice);
            instancePanel.select(updated); // keeps it selected; the Launch bar refreshes by itself
        });
        return versionPicker;
    }

    /** Puts "Latest" plus every supported version into the dropdown. */
    private void refreshPickerItems() {
        List<String> choices = new ArrayList<>();
        choices.add(Instance.LATEST);
        for (MinecraftVersion version : instancePanel.getVersions()) {
            choices.add(version.id());
        }
        updatingPicker = true;
        versionPicker.getItems().setAll(choices);
        versionPicker.setValue(selected != null ? selected.version() : null);
        updatingPicker = false;
        if (runningLaunch == null) {
            showInstance(selected); // "Latest" can now show its real version number
        }
    }

    /**
     * Fill the Launch bar with the given instance (or show "none selected").
     * While a game is launching or running, the Launch bar keeps showing that game instead.
     */
    private void showInstance(Instance instance) {
        selected = instance;
        if (runningLaunch != null) {
            return;
        }
        status.setText("");
        versionPicker.getStyleClass().remove("needs-version");

        updatingPicker = true;
        versionPicker.setValue(instance != null ? instance.version() : null);
        updatingPicker = false;

        if (instance == null) {
            launchBadge.getChildren().clear();
            instanceName.setText("No instance selected");
            versionPicker.setDisable(true);
            launchButton.setDisable(true);
            quickPlayButton.setVisible(false);
            return;
        }
        launchBadge.getChildren().setAll(InstanceBadge.create(instance, 48));
        instanceName.setText(instance.name());
        versionPicker.setDisable(false);
        loaderChip.setText(instance.loaderLabel());
        int globalRam = SettingsStore.get().ramMb;
        ramChip.setText("RAM: " + (instance.ramMb() > 0 ? TorcherRig.formatGb(instance.ramMb())
                : globalRam > 0 ? TorcherRig.formatGb(globalRam) : "Auto"));

        updateQuickPlay(instance);

        if (!instance.hasVersion()) {
            // First time opening this instance: ask the player to pick its version.
            versionPicker.getStyleClass().add("needs-version"); // glowing edge on the dropdown
            status.setText("Choose which Minecraft version " + instance.name()
                    + " plays. It's saved, and you can change it any time.");
            launchButton.setDisable(true);
        } else {
            launchButton.setDisable(false);
        }
    }

    // ------------------------------------------------------------------ quick play

    /**
     * Shows the Quick Play button when there's something to jump back into: the last server joined
     * through Nexus, or otherwise this instance's most recently played world (1.20+ can open worlds
     * directly).
     */
    private void updateQuickPlay(Instance instance) {
        quickPlayTarget = null;
        ServerStore.Server last = servers.lastJoined();
        MinecraftVersion version = instancePanel.resolveVersion(instance);
        if (last != null) {
            quickPlayTarget = QuickPlay.joinServer(last.address());
            quickPlayButton.setText("Rejoin " + last.name());
        } else if (version != null && QuickPlay.supportsWorlds(version.id())) {
            String world = latestWorld(instance);
            if (world != null) {
                quickPlayTarget = QuickPlay.openWorld(world);
                quickPlayButton.setText("Continue \"" + world + "\"");
            }
        }
        quickPlayButton.setVisible(quickPlayTarget != null && instance.hasVersion());
    }

    /** The folder name of the instance's most recently played singleplayer world, or null. */
    private static String latestWorld(Instance instance) {
        java.nio.file.Path saves = NexusPaths.instanceDir(instance).resolve("saves");
        if (!java.nio.file.Files.isDirectory(saves)) {
            return null;
        }
        try (java.util.stream.Stream<java.nio.file.Path> worlds = java.nio.file.Files.list(saves)) {
            return worlds.filter(world -> java.nio.file.Files.exists(world.resolve("level.dat")))
                    .max(java.util.Comparator.comparingLong(world -> world.resolve("level.dat").toFile().lastModified()))
                    .map(world -> world.getFileName().toString())
                    .orElse(null);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * One-click join (from a server card anywhere in the launcher): starts the selected instance and
     * goes straight into the server.
     */
    public void joinServer(ServerStore.Server server) {
        if (runningLaunch != null) {
            Toast.info("Minecraft is already running – close it first to join another server.");
            return;
        }
        if (selected == null || !selected.hasVersion()) {
            Toast.error("Pick an instance and choose its version first (on the left and in the Launch bar).");
            return;
        }
        Toast.info("Joining " + server.name() + " with \"" + selected.name() + "\"…");
        launch(QuickPlay.joinServer(server.address()), false);
    }

    /**
     * Safe mode (Tools → Safe Mode): starts the selected instance with all mods switched off,
     * to check whether a mod is causing problems.
     */
    public void launchSafeMode() {
        if (selected == null || !selected.hasVersion()) {
            Toast.error("Pick an instance and choose its version first.");
            return;
        }
        Toast.info("Starting \"" + selected.name() + "\" in safe mode (no mods)…");
        launch(null, true);
    }

    // ------------------------------------------------------------------ launching

    /**
     * LAUNCH clicked (or Quick Play / Join): download what's needed and start the game, in the background.
     *
     * @param quickPlay a server or world to go straight to, or null for the title screen
     * @param safeMode  start without any mods
     */
    private void launch(QuickPlay quickPlay, boolean safeMode) {
        launch(quickPlay, safeMode, false, false);
    }

    /** True while the pre-launch check runs (so a double-click doesn't start two launches). */
    private boolean checking = false;

    /** Game Error Scan → "Run again with extra logging". */
    public void launchWithExtraLogging() {
        if (selected == null || !selected.hasVersion()) {
            Toast.error("Pick an instance and choose its version first.");
            return;
        }
        Toast.info("Starting \"" + selected.name() + "\" with extra logging…");
        launch(null, false, true, false);
    }

    /**
     * @param extraLogging ask the game to write more detail into its log
     * @param checked      the pre-launch check has already run (or was skipped)
     */
    private void launch(QuickPlay quickPlay, boolean safeMode, boolean extraLogging, boolean checked) {
        if (selected == null || runningLaunch != null || checking) {
            return;
        }
        // Game Error Scan: check for problems BEFORE starting (skipped in safe mode – no mods load then).
        MinecraftVersion checkVersion = instancePanel.resolveVersion(selected);
        if (!checked && !safeMode && checkVersion != null) {
            checking = true;
            Instance toCheck = selected;
            status.setText("Checking for problems…");
            Background.run("pre-launch-check", () -> ErrorScanner.preLaunchCheck(toCheck, checkVersion.id()),
                    problems -> {
                        checking = false;
                        status.setText("");
                        if (problems.isEmpty()) {
                            launch(quickPlay, safeMode, extraLogging, true);
                        } else {
                            ErrorScanUi.preLaunch(problems, toCheck, checkVersion.id(), store,
                                    () -> launch(quickPlay, safeMode, extraLogging, true));
                        }
                    },
                    error -> {
                        checking = false;
                        launch(quickPlay, safeMode, extraLogging, true); // the check itself failed – just play
                    });
            return;
        }
        if (quickPlay != null && quickPlay.server() != null) {
            servers.rememberJoined(servers.find(quickPlay.server()));
        }
        MinecraftVersion version = instancePanel.resolveVersion(selected);
        if (version == null) {
            status.setText("The Minecraft version list hasn't loaded yet – check your internet connection.");
            return;
        }
        // Remember when this instance was last played (for Quick Play and playtime stats later).
        Instance playing = store.update(selected.withLastPlayed(System.currentTimeMillis()));
        instancePanel.select(playing);
        GameLaunchTask task = new GameLaunchTask(playing, version, accounts, accounts.getActive(), quickPlay);
        task.setSafeMode(safeMode);
        task.setExtraLogging(extraLogging);
        long launchStart = System.currentTimeMillis();
        // When the game is up: hide/minimise the launcher (Settings → General) and show Discord status.
        task.setOnGameStarted(() -> {
            WindowBehaviour.gameStarted();
            DiscordPresence.show("Playing Minecraft " + version.id());
        });
        runningLaunch = task;
        versionPicker.setDisable(true); // no changing the version while it's launching/running

        // Turn LAUNCH into STOP.
        launchButton.setText("STOP");
        launchButton.setGraphic(Icons.create(Icons.STOP, 18));
        launchButton.getStyleClass().add("stop-mode");
        launchButton.setTooltip(new Tooltip("Force-close the game (or cancel the download)"));

        progressBar.setVisible(true);
        // "bind" = the status text and progress bar now follow the task automatically.
        status.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(event -> {
            int exitCode = task.getValue();
            if (task.wasStoppedByPlayer()) {
                finishLaunch("Minecraft was stopped.");
            } else if (exitCode == 0) {
                finishLaunch("Minecraft closed. See you next time!");
            } else {
                finishLaunch("Minecraft closed with an error (code " + exitCode + "). Looking into it…");
                // Game Error Scan: read the logs and explain what happened.
                Background.run("crash-scan", () -> new Object[] {
                        ErrorScanner.analyseCrash(playing, launchStart), ErrorScanner.collectLogs(playing, launchStart)},
                        result -> {
                            @SuppressWarnings("unchecked")
                            List<Diagnosis> problems = (List<Diagnosis>) result[0];
                            status.setText("Minecraft crashed: " + problems.get(0).title() + ".");
                            ErrorScanUi.crashed(problems, playing, version.id(), store, (String) result[1],
                                    this::launchWithExtraLogging);
                        }, null);
            }
        });
        task.setOnCancelled(event -> finishLaunch("Launch cancelled."));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (error instanceof LoginException) {
                finishLaunch(error.getMessage()); // sign-in messages are already written for players
            } else {
                error.printStackTrace(); // technical details go to the terminal
                finishLaunch("Couldn't launch: " + error.getMessage());
            }
        });

        Thread thread = new Thread(task, "game-launch");
        thread.setDaemon(true); // closing the launcher doesn't wait for this (the game keeps running)
        thread.start();
    }

    /** Puts the Launch bar back to normal after the game closed or launching failed. */
    private void finishLaunch(String message) {
        runningLaunch = null;
        WindowBehaviour.gameClosed();   // bring the launcher back if it was hidden
        playtimeText.set(Playtime.format(Playtime.totalSeconds()));
        DiscordPresence.clear();
        status.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setVisible(false);
        // Turn STOP back into LAUNCH.
        launchButton.setText("LAUNCH");
        launchButton.setGraphic(Icons.create(Icons.PLAY, 22));
        launchButton.getStyleClass().remove("stop-mode");
        launchButton.setTooltip(null);
        showInstance(selected); // in case a different instance was picked while playing
        status.setText(message);
    }
}
