package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.errorscan.Diagnosis;
import com.nexusclient.launcher.errorscan.ErrorScanner;
import com.nexusclient.launcher.errorscan.Fixer;
import com.nexusclient.launcher.errorscan.ScanHistory;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * ErrorScanPage – the Errors tab (the project brief: Game Error Scan, its own tab):
 *
 *   Scan      – check the selected instance for problems (mods, missing mods, damaged files) and read
 *               its last crash, with "Fix it?" buttons
 *   History   – past scans and fixes, with "Undo fix"
 *   Game Logs – the game's log files, with errors highlighted and a search box
 */
public class ErrorScanPage extends VBox {

    private final InstanceStore store;
    private final InstancePanel panel;
    private final HomePage home;
    private final Runnable showHome;
    private final VBox body = new VBox(14);
    private final TabStrip tabs;
    private String currentTab = "Scan";

    public ErrorScanPage(InstanceStore store, InstancePanel panel, HomePage home, Runnable showHome) {
        this.store = store;
        this.panel = panel;
        this.home = home;
        this.showHome = showHome;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.ERROR_SCAN.title());
        title.getStyleClass().add("page-title");
        Label description = new Label(Page.ERROR_SCAN.description());
        description.getStyleClass().add("page-description");
        description.setWrapText(true); // wrap onto more lines in a narrow window instead of being cut off
        tabs = new TabStrip(Page.ERROR_SCAN.tabs(), this::showTab);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(new VBox(4, title, description), tabs, scroll);
        panel.selectedInstanceProperty().addListener((observable, was, now) -> {
            if (!currentTab.equals("History")) {
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
            case "History" -> showHistory();
            case "Game Logs" -> showLogs();
            default -> showScan();
        }
    }

    // ================================================================== Scan

    private void showScan() {
        Instance instance = panel.selectedInstanceProperty().get();
        MinecraftVersion version = instance == null ? null : panel.resolveVersion(instance);
        if (version == null) {
            body.getChildren().setAll(note("Pick an instance on the left (and choose its version) to scan it."));
            return;
        }
        Label title = new Label("Scan \"" + instance.name() + "\"");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("Checks the mods (missing mods, mods that don't work together, duplicates), the game "
                + "files, and the logs from the last time it was played. Nexus also runs this check automatically "
                + "before every launch.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);
        Button scan = new Button("Scan now");
        scan.getStyleClass().add("primary-button");
        VBox results = new VBox(10);
        scan.setOnAction(event -> {
            scan.setDisable(true);
            results.getChildren().setAll(note("Scanning…"));
            Background.run("scan", () -> {
                List<Diagnosis> found = new ArrayList<>(ErrorScanner.preLaunchCheck(instance, version.id()));
                String logs = ErrorScanner.collectLogs(instance, 0);
                found.addAll(ErrorScanner.analyseText(logs));
                return new Object[] {found, logs};
            }, result -> {
                scan.setDisable(false);
                @SuppressWarnings("unchecked") List<Diagnosis> found = (List<Diagnosis>) result[0];
                String logs = (String) result[1];
                ScanHistory.add(instance.id(), instance.name(), "MANUAL", found, null, List.of());
                results.getChildren().clear();
                if (found.isEmpty()) {
                    results.getChildren().add(note("No problems found. If the game still crashes, try "
                            + "\"Run again with extra logging\", or safe mode in Tools."));
                }
                for (Diagnosis problem : found) {
                    results.getChildren().add(ErrorScanUi.card(problem, instance, version.id(), store, "MANUAL",
                            this::showScan));
                }
                Button again = new Button("Run again with extra logging");
                again.getStyleClass().add("secondary-button");
                again.setOnAction(click -> {
                    showHome.run();
                    home.launchWithExtraLogging();
                });
                results.getChildren().add(new HBox(10, ErrorScanUi.copyReportButton(instance, version.id(), logs),
                        ErrorScanUi.sendReportButton(instance, version.id(), logs), again));
            }, null);
        });
        VBox card = new VBox(12, title, text, scan);
        card.getStyleClass().add("settings-card");
        body.getChildren().setAll(card, results);
    }

    // ================================================================== History

    private void showHistory() {
        List<ScanHistory.Entry> entries = ScanHistory.list();
        VBox list = new VBox(8);
        for (ScanHistory.Entry entry : entries) {
            String kind = switch (entry.kind()) {
                case "CRASH" -> "Crash";
                case "PRE_LAUNCH" -> "Before launch";
                default -> "Scan";
            };
            Label title = new Label(kind + "  ·  " + entry.instanceName() + "  ·  "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.time())));
            title.getStyleClass().add("mod-title");
            Label problems = new Label(entry.problems().isEmpty() ? "No problems found"
                    : String.join("  ·  ", entry.problems()));
            problems.getStyleClass().add("mod-meta");
            problems.setWrapText(true);
            VBox text = new VBox(3, title, problems);
            if (entry.fixDescription() != null) {
                Label fix = new Label((entry.undone() ? "Undone: " : "Fixed: ") + entry.fixDescription());
                fix.getStyleClass().add(entry.undone() ? "mod-meta" : "menu-account-active");
                fix.setWrapText(true);
                text.getChildren().add(fix);
            }
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(12, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mod-row");
            if (entry.canUndo()) {
                Button undo = new Button("Undo fix");
                undo.getStyleClass().add("secondary-button");
                undo.setOnAction(event -> Modal.confirm("Undo this fix?", entry.fixDescription()
                        + "\n\nEverything it changed will be put back.", "Undo", () -> Background.run("undo", () -> {
                    Fixer.undo(entry, store);
                    return null;
                }, done -> {
                    Toast.info("Fix undone.");
                    showHistory();
                }, null)));
                row.getChildren().add(undo);
            }
            list.getChildren().add(row);
        }
        if (entries.isEmpty()) {
            list.getChildren().add(note("No scans yet."));
        }
        body.getChildren().setAll(list);
    }

    // ================================================================== Game logs

    private void showLogs() {
        Instance instance = panel.selectedInstanceProperty().get();
        if (instance == null) {
            body.getChildren().setAll(note("Pick an instance on the left to see its game logs."));
            return;
        }
        List<Path> files = logFiles(instance);
        if (files.isEmpty()) {
            body.getChildren().setAll(note("\"" + instance.name() + "\" has no logs yet – they appear after it's played."));
            return;
        }
        ComboBox<Path> picker = new ComboBox<>(FXCollections.observableArrayList(files));
        picker.getStyleClass().add("form-combo");
        picker.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Path file, boolean empty) {
                super.updateItem(file, empty);
                setText(empty || file == null ? null : NexusPaths.instanceDir(instance).relativize(file).toString());
            }
        });
        picker.setButtonCell(picker.getCellFactory().call(null));
        TextField search = new TextField();
        search.setPromptText("Search the log…");
        HBox.setHgrow(search, Priority.ALWAYS);

        ObservableList<String> lines = FXCollections.observableArrayList();
        FilteredList<String> shown = new FilteredList<>(lines);
        ListView<String> view = new ListView<>(shown);
        view.getStyleClass().add("log-view");
        view.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().removeAll("log-error", "log-warn");
                setText(empty ? null : line);
                if (!empty && line != null) {
                    if (line.contains("ERROR") || line.contains("Exception") || line.startsWith("Caused by")
                            || line.contains("FATAL")) {
                        getStyleClass().add("log-error");
                    } else if (line.contains("WARN")) {
                        getStyleClass().add("log-warn");
                    }
                }
            }
        });
        VBox.setVgrow(view, Priority.ALWAYS);
        view.setPrefHeight(480);
        search.textProperty().addListener((observable, was, now) -> {
            String query = now.toLowerCase();
            shown.setPredicate(line -> query.isEmpty() || line.toLowerCase().contains(query));
        });
        picker.setOnAction(event -> Background.run("read-log", () -> readLog(picker.getValue()),
                text -> lines.setAll(Arrays.asList(text.split("\\R"))), null));
        picker.getSelectionModel().selectFirst();
        picker.getOnAction().handle(null);

        Button folder = new Button("Open logs folder");
        folder.getStyleClass().add("secondary-button");
        folder.setOnAction(event -> Background.run("open-logs", () -> {
            java.awt.Desktop.getDesktop().open(NexusPaths.instanceDir(instance).resolve("logs").toFile());
            return null;
        }, done -> { }, null));
        body.getChildren().setAll(new HBox(10, picker, search, folder), view);
    }

    /** The instance's log files and crash reports, newest first. */
    private static List<Path> logFiles(Instance instance) {
        List<Path> files = new ArrayList<>();
        for (String folder : List.of("logs", "crash-reports")) {
            Path dir = NexusPaths.instanceDir(instance).resolve(folder);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> list = Files.list(dir)) {
                    list.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".log") || path.toString().endsWith(".log.gz")
                                    || path.toString().endsWith(".txt"))
                            .forEach(files::add);
                } catch (Exception ignored) {
                    // unreadable folder
                }
            }
        }
        files.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
        return files;
    }

    /** Reads a log (old logs are squeezed into .gz files – unsqueeze those). */
    private static String readLog(Path file) throws Exception {
        try (InputStream in = file.toString().endsWith(".gz")
                ? new GZIPInputStream(Files.newInputStream(file)) : Files.newInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Node note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("torcher-text");
        label.setWrapText(true);
        return label;
    }
}
