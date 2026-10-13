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
import com.nexusclient.launcher.mods.ModManager;
import com.nexusclient.launcher.online.NexusOnline;
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

    /** Below this page width, the Friends/Party column is hidden so the main column has room. */
    private static final double SOCIAL_COLUMN_MIN_PAGE_WIDTH = 900;

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

    // The Friends/Party column and the Friends stat tile (filled from the Nexus online server).
    private final Label friendsCount = new Label("");
    private final VBox friendsList = new VBox();
    private final VBox partyList = new VBox();
    private com.google.gson.JsonObject friendsOverview;
    private Label friendsOnlineLabel;

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
        VBox social = buildSocialColumn();
        HBox columns = new HBox(24, scroll, social);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        setCenter(columns);

        // In a narrow window, hide the Friends/Party column (it's all in the Friends tab too), so the
        // main column keeps enough room instead of everything getting squashed.
        widthProperty().addListener((observable, oldWidth, width) -> {
            boolean roomy = width.doubleValue() >= SOCIAL_COLUMN_MIN_PAGE_WIDTH;
            social.setVisible(roomy);
            social.setManaged(roomy); // "managed = false" means the layout leaves no gap for it
        });

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

    /** The welcome banner: a greeting, live status chips, and four stat tiles that flip over. */
    private VBox buildBanner(InstancePanel instancePanel) {
        Label eyebrow = new Label();
        eyebrow.getStyleClass().add("banner-eyebrow");
        eyebrow.textProperty().bind(Bindings.createStringBinding(() -> (accounts.getActive() == null
                ? "WELCOME TO NEXUS" : "WELCOME BACK") + "  ·  DEV BUILD 0.1.0", accounts.activeProperty()));
        Label title = new Label();
        title.getStyleClass().addAll("banner-title", "private"); // your name – hidden in streamer mode
        title.textProperty().bind(Bindings.createStringBinding(() -> {
            AccountManager.SavedAccount active = accounts.getActive();
            return active == null ? "Ready to play" : active.username();
        }, accounts.activeProperty()));
        title.setMinWidth(0);

        // Live status chips: is the Nexus online server up, and is the Nexus mod ready?
        Label serverChip = new Label("Nexus servers", dot("status-dot-wait"));
        serverChip.getStyleClass().add("status-chip");
        NexusOnline.get().addStateListener(state -> serverChip.setGraphic(dot(switch (state) {
            case ONLINE -> "status-dot-on";
            case CONNECTING -> "status-dot-wait";
            default -> "status-dot-off";
        })));
        Label modChip = new Label("Nexus mod ready",
                Icons.create(Icons.BOLT, 11));
        modChip.getStyleClass().add("status-chip");
        HBox chips = new HBox(8, serverChip, modChip);

        // Four stats. Click one to flip it over and see the details behind the number.
        FlipTile instancesTile = new FlipTile(Icons.SERVERS, "INSTANCES", () -> instancePanel.getInstances().stream()
                .limit(5).map(i -> i.name() + "  ·  " + (i.hasVersion() ? i.version() : "no version")).toList());
        FlipTile versionTile = new FlipTile(Icons.VERSION, "LATEST", () -> List.of(
                "Newest Minecraft: " + instancePanel.latestVersionProperty().get(),
                "Nexus mod on: " + ModManager.nexusModVersionsText()));
        FlipTile playtimeTile = new FlipTile(Icons.PLAY, "PLAYTIME", this::playtimeDetails);
        FlipTile friendsTile = new FlipTile(Icons.FRIENDS, "FRIENDS ONLINE", this::friendDetails);

        // The numbers count up from 0 when Nexus opens, then follow the real values.
        Animations.countUp(instancesTile.value(), instancePanel.getInstances().size(), "");
        instancePanel.getInstances().addListener((ListChangeListener<Instance>) change ->
                instancesTile.value().setText(String.valueOf(instancePanel.getInstances().size())));
        versionTile.value().textProperty().bind(instancePanel.latestVersionProperty());
        playtimeTile.value().textProperty().bind(playtimeText);
        friendsOnlineLabel = friendsTile.value();

        HBox stats = new HBox(12, instancesTile, versionTile, playtimeTile, friendsTile);
        stats.getStyleClass().add("banner-stats");

        // The words on the left, the floating neon block on the right.
        VBox words = new VBox(8, eyebrow, title, chips);
        HBox.setHgrow(words, Priority.ALWAYS);
        NeonCube cube = new NeonCube();
        HBox top = new HBox(words, cube);
        top.setAlignment(Pos.CENTER_LEFT);
        // The neon block steps aside when the banner gets narrow, to leave room for the words.
        top.widthProperty().addListener((observable, oldWidth, width) -> {
            boolean roomy = width.doubleValue() >= 480;
            cube.setVisible(roomy);
            cube.setManaged(roomy);
        });

        VBox banner = new VBox(4, top, stats);
        banner.getStyleClass().add("home-banner");
        return banner;
    }

    /** A small coloured dot (green = online, amber = connecting, grey = offline). */
    private static Region dot(String style) {
        Region dot = new Region();
        dot.getStyleClass().addAll("status-dot", style);
        return dot;
    }

    /** The back of the Playtime tile: total, the most-played instances, and sessions. */
    private List<String> playtimeDetails() {
        Playtime.Data data = Playtime.load();
        List<String> lines = new ArrayList<>();
        lines.add("Total: " + Playtime.format(Playtime.totalSeconds()) + "  ·  " + data.recentSessions.size() + " sessions");
        data.secondsByInstance.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(entry -> lines.add(instanceName(entry.getKey()) + ": " + Playtime.format(entry.getValue())));
        return lines;
    }

    private String instanceName(String id) {
        return instancePanel.getInstances().stream().filter(i -> i.id().equals(id)).map(Instance::name)
                .findFirst().orElse("Deleted instance");
    }

    /** The back of the Friends tile: who's online and where. */
    private List<String> friendDetails() {
        if (friendsOverview == null) {
            return List.of(NexusOnline.get().state() == NexusOnline.State.ONLINE ? "Loading…" : "Not connected to Nexus yet");
        }
        List<String> lines = new ArrayList<>();
        for (var element : friendsOverview.getAsJsonArray("friends")) {
            var friend = element.getAsJsonObject();
            if (friend.get("online").getAsBoolean()) {
                String server = friend.has("server") && !friend.get("server").isJsonNull() ? friend.get("server").getAsString() : "";
                lines.add("● " + friend.get("name").getAsString() + (server.isEmpty() ? "" : "  ·  " + server));
            }
        }
        return lines.isEmpty() ? List.of("Nobody's online right now") : lines;
    }

    /** "Latest news" heading and a row of cards. */
    private VBox buildNews() {
        HBox cards = new HBox(16);
        int index = 0;
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
            // Each card gets its own accent colour along the top: cyan, violet, magenta, cyan…
            card.getStyleClass().addAll("news-card", "news-card-" + (index++ % 3));
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
        Region accentBar = new Region(); // the little glowing gradient bar in front of the heading
        accentBar.getStyleClass().add("section-bar");
        HBox header = new HBox(10, accentBar, heading);
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

    /** The column on the right: live friends list and party (from the Nexus online server). */
    private VBox buildSocialColumn() {
        friendsCount.getStyleClass().add("social-count");
        VBox friends = socialCard("FRIENDS", friendsCount, friendsList);
        VBox.setVgrow(friends, Priority.ALWAYS); // friends take the spare height
        VBox party = socialCard("PARTY", null, partyList);
        showSocialMessage(friendsList, Icons.FRIENDS, "Connecting…");
        showSocialMessage(partyList, Icons.CLANS, "Not in a party");

        VBox column = new VBox(16, friends, party);
        column.getStyleClass().add("social-column");

        // Sign in to Nexus in the background, then keep the lists fresh from live events.
        NexusOnline online = NexusOnline.get();
        online.addListener(event -> {
            String type = event.get("type").getAsString();
            if (type.startsWith("friend.") || type.startsWith("party.")) {
                refreshSocial();
            }
        });
        Background.run("home-social", () -> {
            online.signIn(accounts);
            return true;
        }, ok -> refreshSocial(), error -> showSocialMessage(friendsList, Icons.FRIENDS,
                accounts.getActive() == null ? "Sign in to see friends" : "Nexus servers offline"));
        accounts.activeProperty().addListener((o, a, b) -> Background.run("home-social", () -> {
            online.signIn(accounts);
            return true;
        }, ok -> refreshSocial(), error -> { }));
        return column;
    }

    /** Reloads friends and party from the Nexus server. */
    private void refreshSocial() {
        NexusOnline online = NexusOnline.get();
        Background.run("home-social", () -> new com.google.gson.JsonElement[] {online.get("/api/friends"), online.get("/api/party")},
                result -> {
                    friendsOverview = result[0].getAsJsonObject();
                    var p = result[1].getAsJsonObject().get("party");
                    showFriends();
                    showParty(p == null || p.isJsonNull() ? null : p.getAsJsonObject());
                }, error -> { });
    }

    /** Online friends first, each with their head, name, and the server they're on. */
    private void showFriends() {
        List<com.google.gson.JsonObject> list = new ArrayList<>();
        friendsOverview.getAsJsonArray("friends").forEach(e -> list.add(e.getAsJsonObject()));
        list.sort((a, b) -> Boolean.compare(b.get("online").getAsBoolean(), a.get("online").getAsBoolean()));
        long onlineCount = list.stream().filter(f -> f.get("online").getAsBoolean()).count();
        friendsCount.setText(onlineCount + " online");
        if (friendsOnlineLabel != null) {
            Animations.countUp(friendsOnlineLabel, onlineCount, "");
        }
        if (list.isEmpty()) {
            showSocialMessage(friendsList, Icons.FRIENDS, "No friends yet");
            return;
        }
        friendsList.getChildren().clear();
        for (var friend : list.subList(0, Math.min(8, list.size()))) {
            boolean isOnline = friend.get("online").getAsBoolean();
            String server = friend.has("server") && !friend.get("server").isJsonNull() ? friend.get("server").getAsString() : "";
            friendsList.getChildren().add(personRow(friend.get("uuid").getAsString(), friend.get("name").getAsString(),
                    isOnline ? (server.isEmpty() ? "Online" : server) : "Offline", isOnline));
        }
        Animations.flipInCards(friendsList);
    }

    private void showParty(com.google.gson.JsonObject party) {
        if (party == null) {
            showSocialMessage(partyList, Icons.CLANS, "Not in a party");
            return;
        }
        partyList.getChildren().clear();
        String leader = party.get("leader").getAsString();
        for (var element : party.getAsJsonArray("members")) {
            var member = element.getAsJsonObject();
            String uuid = member.get("uuid").getAsString();
            partyList.getChildren().add(personRow(uuid, member.get("name").getAsString(),
                    uuid.equals(leader) ? "Leader" : "Member", member.get("online").getAsBoolean()));
        }
    }

    /** One person: skin head with a status dot, name, and a quieter line under it. */
    private static HBox personRow(String uuid, String name, String detail, boolean isOnline) {
        StackPane head = SkinHeads.forUuid(uuid, 28);
        Region statusDot = dot(isOnline ? "status-dot-on" : "status-dot-off");
        StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
        StackPane headWithDot = new StackPane(head, statusDot);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().addAll("person-name", "private"); // hidden in streamer mode
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().addAll(isOnline ? "person-detail-online" : "person-detail", "private");
        HBox row = new HBox(10, headWithDot, new VBox(0, nameLabel, detailLabel));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("person-row", "flip-in");
        return row;
    }

    /** A short message with an icon, for an empty list. */
    private static void showSocialMessage(VBox list, String icon, String message) {
        Node iconNode = Icons.create(icon, 26);
        iconNode.getStyleClass().add("empty-icon");
        Label label = new Label(message);
        label.getStyleClass().add("empty-title");
        VBox empty = new VBox(8, iconNode, label);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(18, 0, 18, 0));
        list.getChildren().setAll(empty);
    }

    /** A card with a small heading (and optional count on the right) above a list. */
    private static VBox socialCard(String title, Label count, VBox list) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        HBox header = new HBox(8, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        if (count != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(spacer, count);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        list.setSpacing(6);
        VBox card = new VBox(12, header, scroll);
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
        StackPane holder = new StackPane(glow, launchButton);
        Animations.shine(holder, launchButton, 14); // a light streak sweeps across LAUNCH now and then
        return holder;
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
            // Warn when the in-game Nexus mod (mod menu, HUD, DFR…) won't be in this game.
            MinecraftVersion version = instancePanel.resolveVersion(instance);
            if (version != null && !instance.getsNexusMod(version.id())) {
                status.setText(Instance.FORGE.equals(instance.loaderOrVanilla())
                        ? "On Forge the Nexus mod (Right Shift menu, HUD) runs on 1.20.1 and 26.3."
                        : "The Nexus mod isn't ready for " + version.id() + " yet, so this plays without it. Pick "
                                + ModManager.nexusModVersionsText() + " to get it.");
            }
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
