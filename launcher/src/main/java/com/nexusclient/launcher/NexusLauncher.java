package com.nexusclient.launcher;

import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import com.nexusclient.launcher.torcherrig.HardwareScanner;
import com.nexusclient.launcher.ui.AccountMenu;
import com.nexusclient.launcher.ui.Animations;
import com.nexusclient.launcher.ui.FirstRunOverlay;
import com.nexusclient.launcher.ui.HomePage;
import com.nexusclient.launcher.ui.Modal;
import com.nexusclient.launcher.ui.ModsPage;
import com.nexusclient.launcher.ui.DeveloperPage;
import com.nexusclient.launcher.online.NexusOnline;
import com.nexusclient.launcher.ui.NexusLogo;
import com.nexusclient.launcher.ui.ToolsPage;
import com.nexusclient.launcher.ui.SkinsPage;
import com.nexusclient.launcher.ui.ErrorScanPage;
import com.nexusclient.launcher.ui.ServersPage;
import com.nexusclient.launcher.servers.ServerStore;
import com.nexusclient.launcher.ui.SettingsPage;
import com.nexusclient.launcher.ui.InstancePanel;
import com.nexusclient.launcher.ui.NavRail;
import com.nexusclient.launcher.ui.NeonBackdrop;
import com.nexusclient.launcher.ui.Fonts;
import com.nexusclient.launcher.ui.DisplaySetup;
import com.nexusclient.launcher.ui.Page;
import com.nexusclient.launcher.ui.ClansPage;
import com.nexusclient.launcher.ui.FriendsPage;
import com.nexusclient.launcher.ui.PlaceholderPage;
import com.nexusclient.launcher.ui.Theme;
import com.nexusclient.launcher.ui.TitleBar;
import com.nexusclient.launcher.ui.WindowBehaviour;
import com.nexusclient.launcher.ui.WindowMemory;
import com.nexusclient.launcher.ui.Toast;
import com.nexusclient.launcher.ui.WindowResizer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * NexusLauncher – the starting point of the Nexus desktop app. It builds the main window:
 *
 *   ┌──────────────────────── TitleBar ────────────────────────┐
 *   │ NavRail │ InstancePanel │  the selected tab's page       │
 *   │ (tabs)  │ (instances)   │  (HomePage, or a placeholder)  │
 *   └──────────────────────────────────────────────────────────┘
 *
 * Each of those parts is its own class in the "ui" folder. This file just puts them together.
 *
 * How JavaFX works (the short version):
 *   - A "Stage" is the window itself.
 *   - A "Scene" is everything drawn inside the window.
 *   - The scene is built from "nodes" (labels, buttons, layout boxes) placed inside each other.
 *   - The look (colours, fonts, glow) comes from a CSS file: theme.css.
 */
public class NexusLauncher extends Application {

    // Starting window size in pixels (width x height).
    private static final double WINDOW_WIDTH = 1280;
    private static final double WINDOW_HEIGHT = 760;

    // Smallest size the player can shrink the window to, so the layout never gets squashed.
    private static final double MIN_WIDTH = 1024;
    private static final double MIN_HEIGHT = 600;

    // The pages that have been opened so far, so switching tabs doesn't rebuild them each time.
    private final Map<Page, Node> pages = new EnumMap<>(Page.class);

    // The area on the right where the selected tab's page is shown.
    private final StackPane pageArea = new StackPane();

    // Kept as fields because pages and the first-run screens are created later and need them.
    private InstanceStore store;
    private InstancePanel instances;
    private AccountMenu accountMenu;
    private AccountManager accounts;
    private ServerStore servers;
    private HomePage homePage;
    private StackPane root;
    private NavRail navRail;
    private NeonBackdrop backdrop;

    /** The first-run / "set up your PC" screens while they're showing, otherwise null. */
    private FirstRunOverlay overlay;

    /**
     * main() is where every Java program starts.
     * launch() hands control to JavaFX, which then calls our start() method below.
     */
    public static void main(String[] args) {
        // Interface size and animation smoothness for this screen – must be decided before JavaFX starts.
        DisplaySetup.apply();
        launch(args);
    }

    /**
     * JavaFX calls start() once it's ready. "stage" is the main window it made for us.
     */
    @Override
    public void start(Stage stage) {
        // Hide Windows' own white title bar – TitleBar draws a dark one instead.
        stage.initStyle(StageStyle.UNDECORATED);
        // Nexus's title font (Exo 2), so the titles look the same on every PC.
        Fonts.load();

        // The player's Microsoft accounts (loaded from accounts.json + the secure secret store).
        accounts = new AccountManager();
        // The account button in the corner. Sign-in opens Microsoft's page in the player's browser.
        accountMenu = new AccountMenu(accounts, url -> getHostServices().showDocument(url));
        TitleBar titleBar = new TitleBar(stage, accountMenu.button());

        // The player's saved instances (loaded from instances.json).
        store = new InstanceStore();
        instances = new InstancePanel(store);

        // The Home page's Launch bar follows whichever instance is selected in the panel,
        // and launches the game with the active account.
        // Featured / favourite / recent servers.
        servers = new ServerStore();
        servers.loadPartners(); // Nexus Partner Servers, from the Nexus online server (in the background)
        homePage = new HomePage(instances, store, accounts, servers);
        pages.put(Page.HOME, homePage);

        // Tabs on the far left. Clicking one shows its page.
        navRail = new NavRail(this::showPage);

        // The right-hand side: the page on top, the legal notice underneath.
        pageArea.getStyleClass().add("page-area");
        Label disclaimer = new Label(
                "Not an official Minecraft product. "
                + "Not approved by or associated with Mojang or Microsoft.");
        disclaimer.getStyleClass().add("disclaimer");
        BorderPane.setAlignment(disclaimer, Pos.CENTER);

        // The player's background picture (Settings → Appearance) sits faintly behind the pages.
        Region backgroundLayer = new Region();
        backgroundLayer.getStyleClass().add("background-layer");
        backgroundLayer.setMouseTransparent(true);

        BorderPane content = new BorderPane();
        content.getStyleClass().add("content");
        content.setCenter(new StackPane(backgroundLayer, pageArea));
        content.setBottom(disclaimer);
        HBox.setHgrow(content, Priority.ALWAYS); // content takes all the width left over

        // Tabs | instances | content, side by side.
        HBox body = new HBox(navRail, instances, content);

        BorderPane window = new BorderPane();
        window.getStyleClass().add("window");
        window.setTop(titleBar);
        window.setCenter(body);

        // The animated neon glow behind everything. The title bar, tabs and panels are partly
        // see-through ("glass"), so it shows through them.
        backdrop = new NeonBackdrop();
        StackPane windowWithBackdrop = new StackPane(backdrop, window);

        // The very top layer: the window, with room on top for the first-run screens and
        // pop-up messages ("toasts").
        root = new StackPane(windowWithBackdrop);
        Toast.setLayer(root);
        Modal.setLayer(root);

        showPage(Page.HOME);

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        // Load the neon theme. theme.css sits next to this class inside the "resources" folder.
        scene.getStylesheets().add(
                NexusLauncher.class.getResource("theme.css").toExternalForm());

        // Without Windows' frame we have to add edge-resizing back ourselves.
        // It's switched off while the window is maximised.
        WindowResizer.install(stage, scene, () -> !titleBar.isMaximized());

        // Hover and click animations for every button in the window.
        Animations.installButtonAnimations(scene);

        // Accent colour, background picture, bigger text, colourblind mode, reduced motion.
        Theme.install(scene, backgroundLayer, backdrop);
        // Every piece of text in the player's language (lang/strings_<language>.json).
        com.nexusclient.launcher.i18n.Translator.install(scene, SettingsStore.get().language);
        // Minimise/hide the launcher while the game runs (Settings → General).
        WindowBehaviour.install(stage);

        stage.setTitle("Nexus Client"); // still shown in the taskbar
        stage.getIcons().addAll(NexusLogo.appIcons()); // the Nexus logo in the taskbar and Alt+Tab
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        if (DevTour.folder() == null) {
            // Open where the player left the window last time, and remember it again when it closes.
            WindowMemory.restore(stage);
            WindowMemory.track(stage, titleBar::isMaximized);
        } else {
            stage.centerOnScreen(); // the developer tour never moves or saves the player's real window
        }
        stage.show();
        System.out.println("[Nexus] Interface size " + DisplaySetup.appliedPercent() + "% (output scale "
                + stage.getOutputScaleX() + ", drawn at " + stage.getRenderScaleX() + ")");

        // First time opening Nexus: welcome screen, tutorial, sign-in and TorcherRig.
        Settings settings = SettingsStore.get();
        if (!settings.firstRunComplete || Boolean.getBoolean("nexus.firstRun")) {
            showOverlay(FirstRunOverlay.Start.WELCOME);
        }

        // If the player skipped both TorcherRig and the memory prompt, ask again once they sign in.
        accounts.activeProperty().addListener((observable, oldAccount, newAccount) -> {
            if (newAccount != null && SettingsStore.get().optimisePromptPending && overlay == null) {
                showOverlay(FirstRunOverlay.Start.OPTIMISE);
            }
        });

        // The Developer tab appears only when the Nexus server confirms this is the developer's account.
        NexusOnline online = NexusOnline.get();
        online.addStateListener(state -> navRail.showDeveloperTab(
                state == NexusOnline.State.ONLINE && online.isDeveloper()));
        // Mod Rerender: keep the shared workspace up to date (or removed) once signed in to Nexus.
        com.nexusclient.launcher.rerender.WorkspaceSharing.init(store);
        online.addStateListener(state -> {
            if (state == NexusOnline.State.ONLINE) {
                com.nexusclient.launcher.rerender.WorkspaceSharing.syncLater();
            }
        });
        // If a developer kicks or bans this player, tell them why.
        online.addListener(event -> {
            String type = event.get("type").getAsString();
            String reason = event.has("reason") ? event.get("reason").getAsString() : "";
            if (type.equals("kicked")) {
                Toast.error("You were signed out of Nexus by a developer" + (reason.isEmpty() ? "." : ": " + reason));
            } else if (type.equals("banned")) {
                Toast.error("You're banned from Nexus online features until "
                        + event.get("until").getAsString().replace('T', ' ').substring(0, 16)
                        + (reason.isEmpty() ? "" : " (" + reason + ")"));
            }
        });

        checkForNewHardware();

        // Nexus checks for updates when it opens (and always asks before updating).
        if (settings.checkForUpdates && DevTour.folder() == null) {
            SettingsPage.checkForUpdates(false);
        }

        if (DevTour.folder() != null) {
            DevTour.run(scene, tourStops());
        }
    }

    /** Shows the page for a tab, building it the first time it's opened. */
    private void showPage(Page page) {
        Node node = pages.computeIfAbsent(page, this::createPage);
        pageArea.getChildren().setAll(node);
        // Light up the matching tab, also when the page was opened some other way
        // (e.g. joining a server from the Servers tab jumps to Home).
        navRail.select(page);
        Animations.pageIn(node); // fade + slide the page in
    }

    /** Builds a tab's page: a real page if it's been built, otherwise a "coming soon" preview. */
    private Node createPage(Page page) {
        return switch (page) {
            case SETTINGS -> new SettingsPage(store, instances);
            case MODS -> new ModsPage(store, instances);
            case TOOLS -> new ToolsPage(store, instances, homePage, () -> showPage(Page.HOME));
            case SKINS -> new SkinsPage(accounts);
            case ERROR_SCAN -> new ErrorScanPage(store, instances, homePage, () -> showPage(Page.HOME));
            case SERVERS -> new ServersPage(servers, server -> {
                showPage(Page.HOME); // show the Launch bar, so the player sees the game starting
                homePage.joinServer(server);
            });
            case CLANS -> new ClansPage(accounts);
            case MOD_RERENDER -> new com.nexusclient.launcher.ui.ModRerenderPage(store, instances);
            case DEVELOPER -> new DeveloperPage();
            case FRIENDS -> new FriendsPage(accounts, server -> {
                showPage(Page.HOME);
                homePage.joinServer(server);
            });
            default -> new PlaceholderPage(page);
        };
    }

    /** Shows the first-run screens (or just the TorcherRig step) on top of the window. */
    private void showOverlay(FirstRunOverlay.Start start) {
        overlay = new FirstRunOverlay(start, accountMenu, store, instances, () -> {
            root.getChildren().remove(overlay);
            overlay = null;
        });
        root.getChildren().add(overlay);
    }

    /**
     * If TorcherRig has run before, quietly re-check the hardware in the background. If it's
     * different (new graphics card, more RAM…), suggest running TorcherRig again.
     */
    private void checkForNewHardware() {
        String previous = SettingsStore.get().hardwareFingerprint;
        if (previous == null) {
            return;
        }
        Thread check = new Thread(() -> {
            try {
                String now = HardwareScanner.scan().fingerprint();
                if (!now.equals(previous)) {
                    Platform.runLater(() -> Toast.info("New hardware detected! Run TorcherRig again in "
                            + "Settings → TorcherRig to set Nexus up for it."));
                }
            } catch (Exception e) {
                e.printStackTrace(); // not important enough to bother the player
            }
        }, "hardware-check");
        check.setDaemon(true);
        check.start();
    }

    /** Developer tour: every screen worth a picture (see DevTour). */
    private List<DevTour.Stop> tourStops() {
        List<DevTour.Stop> stops = new ArrayList<>();
        if (overlay != null) {
            FirstRunOverlay tourOverlay = overlay;
            stops.add(new DevTour.Stop("firstrun-welcome", () -> {}, 2600));
            for (int i = 0; i < 5; i++) {
                int step = i;
                stops.add(new DevTour.Stop("firstrun-tutorial-" + (i + 1), () -> tourOverlay.showTutorial(step), 500));
            }
            stops.add(new DevTour.Stop("firstrun-login", tourOverlay::showLogin, 500));
            stops.add(new DevTour.Stop("firstrun-torcherrig", tourOverlay::showOptimise, 500));
            stops.add(new DevTour.Stop("firstrun-ram", tourOverlay::showRamPrompt, 500));
            stops.add(new DevTour.Stop("close-overlay", () -> {
                root.getChildren().remove(tourOverlay);
                overlay = null;
            }, 300));
        }
        for (Page page : Page.values()) {
            stops.add(new DevTour.Stop("page-" + page.name().toLowerCase(), () -> showPage(page),
                    Integer.getInteger("nexus.tourDelay", 700))); // -PnexusTourDelay=ms waits longer (e.g. for online pages)
        }
        // The Settings page is created during the tour, so it's looked up when we get there.
        for (String tab : List.of("General", "Appearance", "Accessibility", "Language", "Game", "TorcherRig", "About")) {
            stops.add(new DevTour.Stop("settings-" + tab.toLowerCase(), () -> {
                if (pages.get(Page.SETTINGS) instanceof SettingsPage settingsPage) {
                    showPage(Page.SETTINGS);
                    settingsPage.showTab(tab);
                }
            }, 600));
        }
        stops.add(new DevTour.Stop("mods-resource-packs", () -> {
            showPage(Page.MODS);
            if (pages.get(Page.MODS) instanceof ModsPage modsPage) {
                modsPage.showTab("Resource Packs");
            }
        }, 4000)); // wait for Modrinth's results and pictures
        stops.add(new DevTour.Stop("servers-featured", () -> showPage(Page.SERVERS), 5000)); // wait for pings
        stops.add(new DevTour.Stop("tools-storage", () -> {
            showPage(Page.TOOLS);
            if (pages.get(Page.TOOLS) instanceof ToolsPage toolsPage) {
                toolsPage.showTab("Storage");
            }
        }, 3000));
        stops.add(new DevTour.Stop("tools-playtime", () -> {
            if (pages.get(Page.TOOLS) instanceof ToolsPage toolsPage) {
                toolsPage.showTab("Playtime");
            }
        }, 600));
        stops.add(new DevTour.Stop("tools-clips", () -> {
            if (pages.get(Page.TOOLS) instanceof ToolsPage toolsPage) {
                toolsPage.showTab("Clips");
            }
        }, 4000)); // previews are made by FFmpeg
        stops.add(new DevTour.Stop("tools-clip-player", () -> {
            if (pages.get(Page.TOOLS) instanceof ToolsPage toolsPage) {
                toolsPage.openNewestClip();
            }
        }, 2500));
        stops.add(new DevTour.Stop("close-clip-player", com.nexusclient.launcher.ui.Modal::close, 300));
        stops.add(new DevTour.Stop("mod-rerender", () -> {
            showPage(Page.MOD_RERENDER);
            if (pages.get(Page.MOD_RERENDER) instanceof com.nexusclient.launcher.ui.ModRerenderPage rerender) {
                rerender.lookUp(System.getProperty("nexus.tourRerender", "NexusTester"));
            }
        }, 3500));
        stops.add(new DevTour.Stop("mod-rerender-mine", () -> {
            if (pages.get(Page.MOD_RERENDER) instanceof com.nexusclient.launcher.ui.ModRerenderPage rerender) {
                rerender.showTab("My Workspace");
            }
        }, 800));
        stops.add(new DevTour.Stop("skins", () -> showPage(Page.SKINS), 1200));
        stops.add(new DevTour.Stop("home", () -> showPage(Page.HOME), 4000));
        stops.add(new DevTour.Stop("instance-settings",
                () -> instances.openSettings(instances.selectedInstanceProperty().get()), 700));
        stops.add(new DevTour.Stop("close-settings", Modal::close, 300));
        return stops;
    }
}
