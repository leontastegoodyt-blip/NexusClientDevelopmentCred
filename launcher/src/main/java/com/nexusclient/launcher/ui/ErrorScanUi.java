package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.errorscan.Diagnosis;
import com.nexusclient.launcher.errorscan.ErrorScanner;
import com.nexusclient.launcher.errorscan.Fixer;
import com.nexusclient.launcher.errorscan.ScanHistory;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * ErrorScanUi – the Game Error Scan's pop-ups and problem cards, shared by the launch (before the
 * game starts / after it crashes) and the Errors tab.
 *
 * Every fix asks first ("Fix it?"), and is recorded in the history so it can be undone.
 */
public final class ErrorScanUi {

    private ErrorScanUi() {}

    /**
     * BEFORE the game starts: problems were found. The player can fix them, launch anyway, or cancel.
     *
     * @param launch starts the game (after fixing, or "Launch anyway")
     */
    public static void preLaunch(List<Diagnosis> problems, Instance instance, String versionId, InstanceStore store,
                                 Runnable launch) {
        Label title = new Label("Nexus found " + (problems.size() == 1 ? "a problem" : problems.size() + " problems")
                + " that may stop the game from starting");
        title.getStyleClass().add("torcher-title");
        title.setWrapText(true);
        VBox cards = new VBox(10);
        for (Diagnosis problem : problems) {
            cards.getChildren().add(card(problem, instance, versionId, store, "PRE_LAUNCH", null));
        }
        Button launchAnyway = new Button("Launch anyway");
        launchAnyway.getStyleClass().add("secondary-button");
        launchAnyway.setOnAction(event -> {
            Modal.close();
            launch.run();
        });
        Button fixAll = new Button("Fix everything and launch");
        fixAll.getStyleClass().add("primary-button");
        fixAll.setDisable(problems.stream().noneMatch(Diagnosis::canFix));
        fixAll.setOnAction(event -> {
            fixAll.setDisable(true);
            fixAll.setText("Fixing…");
            Background.run("fix-all", () -> {
                StringBuilder done = new StringBuilder();
                for (Diagnosis problem : problems) {
                    if (problem.canFix()) {
                        Fixer.Result result = Fixer.apply(problem, instance, versionId, store);
                        ScanHistory.add(instance.id(), instance.name(), "PRE_LAUNCH", List.of(problem),
                                result.description(), result.undo());
                        done.append("• ").append(result.description()).append('\n');
                    }
                }
                return done.toString();
            }, done -> {
                Modal.close();
                Toast.info("Fixed – you can undo this in Errors → History.");
                launch.run();
            }, error -> {
                fixAll.setText("Fix everything and launch");
                fixAll.setDisable(false);
                Toast.error("Couldn't fix it: " + error.getMessage());
            });
        });
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> Modal.close());
        HBox buttons = new HBox(10, cancel, launchAnyway, fixAll);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Modal.show(new VBox(14, title, scroll(cards), buttons), 700);
    }

    /**
     * AFTER a crash: explains what went wrong and offers the fix.
     *
     * @param runAgainWithLogging starts the game again with extra logging (to capture more detail)
     */
    public static void crashed(List<Diagnosis> problems, Instance instance, String versionId, InstanceStore store,
                               String logs, Runnable runAgainWithLogging) {
        ScanHistory.add(instance.id(), instance.name(), "CRASH", problems, null, List.of());
        // Opt-in crash reports (Settings → General): send this one automatically.
        if (com.nexusclient.launcher.settings.SettingsStore.get().sendCrashReports) {
            sendReport(instance, versionId, logs, () -> { }, error -> { });
        }
        Label title = new Label("Minecraft crashed – here's what happened");
        title.getStyleClass().add("torcher-title");
        VBox cards = new VBox(10);
        for (Diagnosis problem : problems) {
            cards.getChildren().add(card(problem, instance, versionId, store, "CRASH", null));
        }
        Button again = new Button("Run again with extra logging");
        again.getStyleClass().add("secondary-button");
        again.setOnAction(event -> {
            Modal.close();
            runAgainWithLogging.run();
        });
        Button report = copyReportButton(instance, versionId, logs);
        Button close = new Button("Close");
        close.getStyleClass().add("secondary-button");
        close.setOnAction(event -> Modal.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, report, again, spacer, close);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Modal.show(new VBox(14, title, scroll(cards), buttons), 720);
    }

    /**
     * One problem: title, plain-English explanation, the log line it came from, and a "Fix it" button
     * that asks first.
     *
     * @param afterFix called after a successful fix (can be null)
     */
    public static Node card(Diagnosis problem, Instance instance, String versionId, InstanceStore store, String kind,
                            Runnable afterFix) {
        Label title = new Label(problem.title());
        title.getStyleClass().add("mod-title");
        Label explanation = new Label(problem.explanation());
        explanation.getStyleClass().add("torcher-text");
        explanation.setWrapText(true);
        VBox card = new VBox(6, title, explanation);
        if (problem.evidence() != null && !problem.evidence().isBlank()) {
            Label evidence = new Label("From the log: " + problem.evidence());
            evidence.getStyleClass().add("mod-meta");
            evidence.setWrapText(true);
            card.getChildren().add(evidence);
        }
        if (problem.canFix()) {
            Button fix = new Button("Fix it: " + problem.fixLabel());
            fix.getStyleClass().add("primary-button");
            fix.setWrapText(true);
            fix.setOnAction(event -> Modal.confirm("Fix it?",
                    problem.fixLabel() + ".\n\nNexus backs up anything it changes – you can undo this in Errors → History.",
                    "Fix it", () -> Background.run("fix", () -> {
                        Fixer.Result result = Fixer.apply(problem, instance, versionId, store);
                        ScanHistory.add(instance.id(), instance.name(), kind, List.of(problem), result.description(),
                                result.undo());
                        return result;
                    }, result -> {
                        Toast.info(result.description() + ". (Undo in Errors → History.)");
                        if (afterFix != null) {
                            afterFix.run();
                        }
                    }, error -> Toast.error("Couldn't fix it: " + error.getMessage()))));
            card.getChildren().add(fix);
        }
        card.getStyleClass().add("mod-row");
        return card;
    }

    /** "Send to Nexus support": sends this one error report (the player chose to). */
    public static Button sendReportButton(Instance instance, String versionId, String logs) {
        Button send = new Button("Send to Nexus support");
        send.getStyleClass().add("secondary-button");
        send.setOnAction(event -> {
            send.setDisable(true);
            sendReport(instance, versionId, logs, () -> Toast.info("Sent – thank you! It helps us fix this."),
                    error -> {
                        send.setDisable(false);
                        Toast.error("Couldn't send it: " + error.getMessage() + " – use Copy error report instead.");
                    });
        });
        return send;
    }

    /** Sends an error report to the Nexus server in the background. */
    public static void sendReport(Instance instance, String versionId, String logs, Runnable whenSent,
                                  java.util.function.Consumer<Throwable> whenFailed) {
        String report = ErrorScanner.errorReport(instance, versionId, logs);
        Background.run("crash-report", () -> com.nexusclient.launcher.online.NexusOnline.get().post("/api/crash-reports",
                        java.util.Map.of("version", com.nexusclient.launcher.integrations.Updater.CURRENT_VERSION,
                                "report", report.length() > 190_000 ? report.substring(report.length() - 190_000) : report)),
                ok -> whenSent.run(), whenFailed);
    }

    /** "Copy error report": system details + the end of the logs, ready to send to Nexus support. */
    public static Button copyReportButton(Instance instance, String versionId, String logs) {
        Button report = new Button("Copy error report");
        report.getStyleClass().add("secondary-button");
        report.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(ErrorScanner.errorReport(instance, versionId, logs));
            Clipboard.getSystemClipboard().setContent(content);
            Toast.info("Error report copied. Paste it when you contact Nexus support.");
        });
        return report;
    }

    private static Node scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        scroll.setMaxHeight(420);
        return scroll;
    }
}
