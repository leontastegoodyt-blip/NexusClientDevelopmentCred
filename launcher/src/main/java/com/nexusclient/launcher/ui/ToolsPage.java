package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.tools.Playtime;
import com.nexusclient.launcher.tools.Storage;
import com.nexusclient.launcher.tools.WorldBackups;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * ToolsPage – the Tools tab (the project brief: Launcher → Tools):
 *
 *   Screenshots    – every instance's screenshots: view, copy, delete, open folder
 *   World Backups  – back up, restore and delete copies of singleplayer worlds
 *   Safe Mode      – start the selected instance with all mods off
 *   Playtime       – hours played per instance and per version
 *   Storage        – how much space Nexus uses, and clean-ups that always ask first
 */
public class ToolsPage extends VBox {

    private final InstanceStore store;
    private final InstancePanel panel;
    private final HomePage home;
    private final Runnable showHome;
    private final VBox body = new VBox(14);
    private final TabStrip tabs;
    private String currentTab = "Screenshots";

    /**
     * @param showHome switches to the Home tab (used by safe mode, so the Launch bar is visible)
     */
    public ToolsPage(InstanceStore store, InstancePanel panel, HomePage home, Runnable showHome) {
        this.store = store;
        this.panel = panel;
        this.home = home;
        this.showHome = showHome;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.TOOLS.title());
        title.getStyleClass().add("page-title");
        Label description = new Label(Page.TOOLS.description());
        description.getStyleClass().add("page-description");

        tabs = new TabStrip(Page.TOOLS.tabs(), this::showTab);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(new VBox(4, title, description), tabs, scroll);
        // Tabs that depend on the selected instance refresh when it changes.
        panel.selectedInstanceProperty().addListener((observable, was, now) -> {
            if (currentTab.equals("World Backups") || currentTab.equals("Safe Mode")) {
                showTab(currentTab);
            }
        });
        showTab(currentTab);
    }

    /** Shows one sub-tab (public so the developer tour can visit it). */
    public void showTab(String tab) {
        currentTab = tab;
        tabs.select(tab);
        switch (tab) {
            case "World Backups" -> showBackups();
            case "Safe Mode" -> showSafeMode();
            case "Playtime" -> showPlaytime();
            case "Storage" -> showStorage();
            default -> showScreenshots();
        }
    }

    // ================================================================== Screenshots

    private record Shot(Instance instance, Path file, long time) {}

    private void showScreenshots() {
        body.getChildren().setAll(note("Loading screenshots…"));
        List<Instance> instances = List.copyOf(store.instances());
        Background.run("screenshots", () -> {
            List<Shot> shots = new ArrayList<>();
            for (Instance instance : instances) {
                Path folder = NexusPaths.instanceDir(instance).resolve("screenshots");
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> files = Files.list(folder)) {
                    files.filter(file -> file.toString().toLowerCase().endsWith(".png"))
                            .forEach(file -> shots.add(new Shot(instance, file, file.toFile().lastModified())));
                }
            }
            shots.sort(Comparator.comparingLong(Shot::time).reversed());
            return shots;
        }, shots -> {
            if (shots.isEmpty()) {
                body.getChildren().setAll(note("No screenshots yet. Press F2 in Minecraft to take one – they'll "
                        + "show up here."));
                return;
            }
            TilePane grid = new TilePane(12, 12);
            grid.setPrefTileWidth(232);
            grid.setMaxWidth(Double.MAX_VALUE);
            for (Shot shot : shots) {
                grid.getChildren().add(screenshotTile(shot));
            }
            body.getChildren().setAll(note(shots.size() + " screenshots. Click one to open it."), grid);
        }, null);
    }

    private Node screenshotTile(Shot shot) {
        ImageView view = new ImageView(new Image(shot.file().toUri().toString(), 232, 130, true, true, true));
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("screenshot-tile");
        frame.setPrefSize(232, 130);
        Label caption = new Label(shot.instance().name() + "  ·  "
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(shot.time())));
        caption.getStyleClass().add("mod-meta");
        VBox tile = new VBox(6, frame, caption);
        tile.setOnMouseClicked(event -> openScreenshot(shot));
        tile.setStyle("-fx-cursor: hand;");
        return tile;
    }

    /** A pop-up with the full screenshot and its buttons. */
    private void openScreenshot(Shot shot) {
        Image image = new Image(shot.file().toUri().toString());
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(820);
        Button copy = new Button("Copy picture");
        copy.getStyleClass().add("primary-button");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(image);
            Clipboard.getSystemClipboard().setContent(content);
            Toast.info("Screenshot copied – paste it anywhere.");
        });
        Button folder = new Button("Open folder");
        folder.getStyleClass().add("secondary-button");
        folder.setOnAction(event -> open(shot.file().getParent()));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(event -> Modal.confirm("Delete this screenshot?", "It can't be recovered.", "Delete",
                () -> Background.run("delete-shot", () -> {
                    Files.deleteIfExists(shot.file());
                    return null;
                }, done -> showScreenshots(), null)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Modal.show(new VBox(12, view, new HBox(10, copy, folder, spacer, delete)), 880);
    }

    // ================================================================== World backups

    private void showBackups() {
        Instance instance = panel.selectedInstanceProperty().get();
        if (instance == null) {
            body.getChildren().setAll(note("Pick an instance on the left to see its worlds and backups."));
            return;
        }
        body.getChildren().setAll(note("Loading…"));
        Background.run("backups", () -> new Object[] {WorldBackups.worlds(instance), WorldBackups.list(instance)}, result -> {
            @SuppressWarnings("unchecked") List<String> worlds = (List<String>) result[0];
            @SuppressWarnings("unchecked") List<WorldBackups.Backup> backups = (List<WorldBackups.Backup>) result[1];

            VBox worldList = new VBox(8);
            for (String world : worlds) {
                Label name = new Label(world);
                name.getStyleClass().add("mod-title");
                long count = backups.stream().filter(backup -> backup.world().equals(world)).count();
                Label detail = new Label(count + (count == 1 ? " backup" : " backups"));
                detail.getStyleClass().add("mod-meta");
                VBox text = new VBox(2, name, detail);
                HBox.setHgrow(text, Priority.ALWAYS);
                Button backUp = new Button("Back up now");
                backUp.getStyleClass().add("primary-button");
                backUp.setOnAction(event -> {
                    backUp.setDisable(true);
                    Background.run("back-up", () -> WorldBackups.backUp(instance, world), done -> {
                        Toast.info("Backed up \"" + world + "\" (" + Storage.format(done.sizeBytes()) + ").");
                        showBackups();
                    }, null);
                });
                HBox row = new HBox(12, text, backUp);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mod-row");
                worldList.getChildren().add(row);
            }
            if (worlds.isEmpty()) {
                worldList.getChildren().add(note("\"" + instance.name() + "\" has no singleplayer worlds yet."));
            }

            VBox backupList = new VBox(8);
            for (WorldBackups.Backup backup : backups) {
                Label name = new Label(backup.world());
                name.getStyleClass().add("mod-title");
                Label detail = new Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(backup.createdMillis())) + "  ·  " + Storage.format(backup.sizeBytes()));
                detail.getStyleClass().add("mod-meta");
                VBox text = new VBox(2, name, detail);
                HBox.setHgrow(text, Priority.ALWAYS);
                Button restore = new Button("Restore");
                restore.getStyleClass().add("secondary-button");
                restore.setOnAction(event -> Modal.confirm("Restore \"" + backup.world() + "\"?",
                        "The world goes back to how it was in this backup. Don't worry: the world as it is now is "
                                + "backed up first, so you can undo this.", "Restore",
                        () -> Background.run("restore", () -> {
                            WorldBackups.restore(instance, backup);
                            return null;
                        }, done -> {
                            Toast.info("Restored \"" + backup.world() + "\".");
                            showBackups();
                        }, null)));
                Button delete = new Button("Delete");
                delete.getStyleClass().add("danger-button");
                delete.setOnAction(event -> Modal.confirm("Delete this backup?", "It can't be recovered.", "Delete",
                        () -> Background.run("delete-backup", () -> {
                            WorldBackups.delete(backup);
                            return null;
                        }, done -> showBackups(), null)));
                HBox row = new HBox(10, text, restore, delete);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mod-row");
                backupList.getChildren().add(row);
            }
            if (backups.isEmpty()) {
                backupList.getChildren().add(note("No backups yet. Nexus backs up the worlds you play automatically "
                        + "after you close the game."));
            }
            body.getChildren().setAll(heading("Worlds in \"" + instance.name() + "\""), worldList,
                    heading("Backups"), backupList);
        }, null);
    }

    // ================================================================== Safe mode

    private void showSafeMode() {
        Instance instance = panel.selectedInstanceProperty().get();
        Label title = new Label("Safe mode");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("Safe mode starts an instance with ALL mods switched off. If the game works in safe "
                + "mode but not normally, one of the mods is causing the problem. Your mods aren't changed or deleted – "
                + "they're just not loaded this one time.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);
        Button start = new Button(instance == null ? "Pick an instance first" : "Start \"" + instance.name() + "\" in safe mode");
        start.getStyleClass().add("primary-button");
        start.setDisable(instance == null);
        start.setOnAction(event -> {
            showHome.run();
            home.launchSafeMode();
        });
        VBox card = new VBox(12, title, text, start);
        card.getStyleClass().add("settings-card");
        body.getChildren().setAll(card);
    }

    // ================================================================== Playtime

    private void showPlaytime() {
        Playtime.Data data = Playtime.load();
        long total = data.secondsByInstance.values().stream().mapToLong(Long::longValue).sum();
        Label totalLabel = new Label(Playtime.format(total));
        totalLabel.getStyleClass().add("ram-value");
        VBox content = new VBox(12, heading("Total playtime"), totalLabel);

        content.getChildren().add(heading("By instance"));
        long most = Math.max(1, data.secondsByInstance.values().stream().mapToLong(Long::longValue).max().orElse(1));
        for (Map.Entry<String, Long> entry : data.secondsByInstance.entrySet()) {
            String name = store.instances().stream().filter(i -> i.id().equals(entry.getKey()))
                    .map(Instance::name).findFirst().orElse("(deleted instance)");
            content.getChildren().add(bar(name, entry.getValue(), most));
        }
        content.getChildren().add(heading("By Minecraft version"));
        long mostVersion = Math.max(1, data.secondsByVersion.values().stream().mapToLong(Long::longValue).max().orElse(1));
        for (Map.Entry<String, Long> entry : data.secondsByVersion.entrySet()) {
            content.getChildren().add(bar(entry.getKey(), entry.getValue(), mostVersion));
        }
        if (data.secondsByInstance.isEmpty()) {
            content.getChildren().add(note("Nothing yet – playtime is counted every time you play through Nexus."));
        }
        VBox card = new VBox(content);
        card.getStyleClass().add("settings-card");
        body.getChildren().setAll(card);
    }

    /** A name, a bar showing how much compared to the most-played, and the time. */
    private static Node bar(String name, long seconds, long most) {
        Label label = new Label(name);
        label.getStyleClass().add("change-name");
        label.setMinWidth(160);
        ProgressBar bar = new ProgressBar(seconds / (double) most);
        bar.getStyleClass().add("launch-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        Label time = new Label(Playtime.format(seconds));
        time.getStyleClass().add("change-value");
        time.setMinWidth(70);
        HBox row = new HBox(12, label, bar, time);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ================================================================== Storage

    private void showStorage() {
        body.getChildren().setAll(note("Measuring… (this can take a few seconds)"));
        Background.run("storage", () -> new Object[] {Storage.measure(), Storage.oldLogs(), Storage.oldBackups(),
                Storage.natives()}, result -> {
            @SuppressWarnings("unchecked") List<Storage.Area> areas = (List<Storage.Area>) result[0];
            long total = areas.stream().mapToLong(Storage.Area::bytes).sum();
            Label totalLabel = new Label(Storage.format(total));
            totalLabel.getStyleClass().add("ram-value");
            VBox usage = new VBox(10, heading("Nexus is using"), totalLabel);
            long biggest = Math.max(1, areas.isEmpty() ? 1 : areas.get(0).bytes());
            for (Storage.Area area : areas) {
                ProgressBar bar = new ProgressBar(area.bytes() / (double) biggest);
                bar.getStyleClass().add("launch-progress");
                bar.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(bar, Priority.ALWAYS);
                Label name = new Label(area.name());
                name.getStyleClass().add("change-name");
                name.setMinWidth(160);
                Label size = new Label(Storage.format(area.bytes()));
                size.getStyleClass().add("change-value");
                size.setMinWidth(70);
                HBox row = new HBox(12, name, bar, size);
                row.setAlignment(Pos.CENTER_LEFT);
                usage.getChildren().add(row);
            }
            Button openFolder = new Button("Open the Nexus folder");
            openFolder.getStyleClass().add("secondary-button");
            openFolder.setOnAction(event -> open(NexusPaths.root()));
            usage.getChildren().add(openFolder);
            VBox usageCard = new VBox(usage);
            usageCard.getStyleClass().add("settings-card");

            VBox cleanups = new VBox(10, heading("Clean up"));
            for (int i = 1; i <= 3; i++) {
                Storage.Cleanup cleanup = (Storage.Cleanup) result[i];
                Label name = new Label(cleanup.name());
                name.getStyleClass().add("mod-title");
                Label detail = new Label(cleanup.files().size() + " files  ·  " + Storage.format(cleanup.bytes()));
                detail.getStyleClass().add("mod-meta");
                VBox text = new VBox(2, name, detail);
                HBox.setHgrow(text, Priority.ALWAYS);
                Button clean = new Button("Clean up");
                clean.getStyleClass().add("danger-button");
                clean.setDisable(cleanup.files().isEmpty());
                clean.setOnAction(event -> Modal.confirm("Delete " + cleanup.files().size() + " files?",
                        cleanup.name() + " – this frees " + Storage.format(cleanup.bytes()) + ".", "Delete",
                        () -> Background.run("cleanup", () -> Storage.run(cleanup), freed -> {
                            Toast.info("Freed " + Storage.format(freed) + ".");
                            showStorage();
                        }, null)));
                HBox row = new HBox(12, text, clean);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mod-row");
                cleanups.getChildren().add(row);
            }
            VBox cleanupCard = new VBox(cleanups);
            cleanupCard.getStyleClass().add("settings-card");
            body.getChildren().setAll(usageCard, cleanupCard);
        }, null);
    }

    // ================================================================== small pieces

    private static void open(Path folder) {
        Background.run("open-folder", () -> {
            Files.createDirectories(folder);
            Desktop.getDesktop().open(folder.toFile());
            return null;
        }, done -> { }, null);
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Node note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("torcher-text");
        label.setWrapText(true);
        return label;
    }
}
