package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import com.nexusclient.launcher.torcherrig.Benchmark;
import com.nexusclient.launcher.torcherrig.HardwareInfo;
import com.nexusclient.launcher.torcherrig.HardwareScanner;
import com.nexusclient.launcher.torcherrig.TorcherRig;
import com.nexusclient.launcher.torcherrig.Tier;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TorcherRigPane – the TorcherRig screen, used in two places:
 *   - the first-run setup (with "No thanks" and "Skip" options)
 *   - Settings → TorcherRig (to run it again, or revert it)
 *
 * It goes through four stages: intro → scanning → results (what will change) → done.
 * Nothing changes until the player presses "Apply changes" (the project brief: always ask first).
 */
public class TorcherRigPane extends VBox {

    /** Where the pane is shown. */
    public enum Mode { FIRST_RUN, SETTINGS }

    /** What the pane tells its owner when the player is finished with it. */
    public interface Listener {
        /** The changes were applied (or the player closed the results in Settings). */
        void done();

        /** First run only: the player said no to TorcherRig – show the manual RAM prompt instead. */
        default void declined() {}
    }

    private final Mode mode;
    private final InstanceStore store;
    private final InstancePanel instancePanel;
    private final Listener listener;

    public TorcherRigPane(Mode mode, InstanceStore store, InstancePanel instancePanel, Listener listener) {
        this.mode = mode;
        this.store = store;
        this.instancePanel = instancePanel;
        this.listener = listener;
        getStyleClass().add("torcher-pane");
        setSpacing(14);
        showIntro();
    }

    // ================================================================== stage 1: intro

    private void showIntro() {
        Label title = new Label("TorcherRig");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("TorcherRig scans your PC – processor, graphics card, memory and screen – and "
                + "sets Nexus up to match it: how much memory the game gets and which video settings it uses. "
                + "You'll see exactly what will change before anything happens, and you can undo it any time.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);

        CheckBox benchmark = new CheckBox("Also run a quick 10-second speed test (more accurate)");
        benchmark.setSelected(true);

        Button scan = new Button("Scan my PC");
        scan.getStyleClass().add("primary-button");
        scan.setOnAction(event -> startScan(benchmark.isSelected()));

        HBox buttons = new HBox(10, scan);
        buttons.setAlignment(Pos.CENTER_LEFT);
        if (mode == Mode.FIRST_RUN) {
            Button no = new Button("No thanks, I'll set memory myself");
            no.getStyleClass().add("secondary-button");
            no.setOnAction(event -> listener.declined());
            buttons.getChildren().add(no);
        }

        List<Node> content = new ArrayList<>(List.of(title, text, benchmark, buttons));
        if (mode == Mode.SETTINGS) {
            content.add(1, currentStatus());
        }
        getChildren().setAll(content);
    }

    /** Settings only: what TorcherRig did last time, with a Revert button. */
    private Node currentStatus() {
        Settings settings = SettingsStore.get();
        Label status = new Label(settings.torcherTier == null
                ? "TorcherRig hasn't been run on this PC yet."
                : "Last result: " + Tier.valueOf(settings.torcherTier).label() + " tier  ·  game memory "
                        + (settings.ramMb > 0 ? TorcherRig.formatGb(settings.ramMb) : "Auto"));
        status.getStyleClass().add("torcher-status");
        HBox row = new HBox(12, status);
        row.setAlignment(Pos.CENTER_LEFT);
        if (TorcherRig.canRevert()) {
            Button revert = new Button("Revert changes");
            revert.getStyleClass().add("secondary-button");
            revert.setOnAction(event -> revert());
            row.getChildren().add(revert);
        }
        return row;
    }

    // ================================================================== stage 2: scanning

    private void startScan(boolean runBenchmark) {
        Label title = new Label("Scanning your PC…");
        title.getStyleClass().add("torcher-title");
        Label message = new Label("Reading your hardware…");
        message.getStyleClass().add("torcher-text");
        ProgressBar bar = new ProgressBar(-1);
        bar.getStyleClass().add("launch-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        getChildren().setAll(title, message, bar);

        Task<Object[]> task = new Task<>() {
            @Override
            protected Object[] call() throws Exception {
                HardwareInfo hardware = HardwareScanner.scan();
                Benchmark.Result speed = null;
                if (runBenchmark) {
                    updateMessage("Running the speed test (about 10 seconds)…");
                    speed = Benchmark.run(fraction -> updateProgress(fraction, 1));
                }
                return new Object[] {hardware, speed, TorcherRig.plan(hardware, speed)};
            }
        };
        message.textProperty().bind(task.messageProperty());
        bar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(event -> {
            Object[] result = task.getValue();
            showResults((HardwareInfo) result[0], (Benchmark.Result) result[1], (TorcherRig.Plan) result[2]);
        });
        task.setOnFailed(event -> {
            task.getException().printStackTrace();
            Toast.error("TorcherRig couldn't scan this PC: " + task.getException().getMessage());
            showIntro();
        });
        Thread thread = new Thread(task, "torcherrig-scan");
        thread.setDaemon(true);
        thread.start();
    }

    // ================================================================== stage 3: results

    private void showResults(HardwareInfo hardware, Benchmark.Result speed, TorcherRig.Plan plan) {
        Label title = new Label("Your PC is " + article(plan.tier()) + " " + plan.tier().label() + " tier PC");
        title.getStyleClass().add("torcher-title");
        Label badge = new Label(plan.tier().label().toUpperCase(Locale.ROOT));
        badge.getStyleClass().addAll("tier-badge", "tier-" + plan.tier().name().toLowerCase(Locale.ROOT));
        HBox heading = new HBox(12, title, badge);
        heading.setAlignment(Pos.CENTER_LEFT);

        // --- What was found ---
        GridPane found = new GridPane();
        found.getStyleClass().add("hardware-grid");
        found.setHgap(18);
        found.setVgap(6);
        int row = 0;
        addRow(found, row++, "Processor", hardware.cpuName() + "  ·  " + hardware.cpuCores() + " cores / "
                + hardware.cpuThreads() + " threads" + (hardware.cpuGhz() > 0
                ? String.format(Locale.ROOT, "  ·  %.1f GHz", hardware.cpuGhz()) : ""));
        List<String> gpus = new ArrayList<>();
        for (HardwareInfo.Gpu gpu : hardware.gpus()) {
            gpus.add(gpu.name() + (gpu.vramMb() > 0 ? " (" + TorcherRig.formatGb((int) gpu.vramMb()) + ")" : ""));
        }
        addRow(found, row++, "Graphics", gpus.isEmpty() ? "Unknown" : String.join("  +  ", gpus));
        addRow(found, row++, "Memory", TorcherRig.formatGb((int) hardware.totalRamMb()) + " total  ·  "
                + TorcherRig.formatGb((int) hardware.freeRamMb()) + " free right now");
        addRow(found, row++, "Screen", hardware.screenWidth() + " × " + hardware.screenHeight()
                + " at " + hardware.refreshRate() + " Hz");
        addRow(found, row++, "System", hardware.osName());
        if (speed != null) {
            addRow(found, row, "Speed test", "One core: " + speed.singleCore() + "  ·  All cores: "
                    + speed.multiCore() + "   (100 ≈ a typical mid-range gaming PC)");
        }

        // --- What will change ---
        Label changesTitle = new Label("What TorcherRig will change");
        changesTitle.getStyleClass().add("section-title");
        VBox changes = new VBox(4);
        for (TorcherRig.Change change : plan.changes()) {
            Label name = new Label(change.setting());
            name.getStyleClass().add("change-name");
            Region dots = new Region();
            HBox.setHgrow(dots, Priority.ALWAYS);
            Label value = new Label(change.value());
            value.getStyleClass().add("change-value");
            HBox line = new HBox(10, name, dots, value);
            line.getStyleClass().add("change-row");
            line.setAlignment(Pos.CENTER_LEFT);
            changes.getChildren().add(line);
        }

        VBox tips = new VBox(4);
        for (String tip : plan.tips()) {
            Label tipLabel = new Label("Tip:  " + tip);
            tipLabel.getStyleClass().add("torcher-tip");
            tipLabel.setWrapText(true);
            tips.getChildren().add(tipLabel);
        }

        // --- Which instances ---
        ToggleGroup scope = new ToggleGroup();
        RadioButton all = new RadioButton("All instances (" + store.instances().size() + ")");
        all.setToggleGroup(scope);
        all.setSelected(true);
        Instance selected = instancePanel.selectedInstanceProperty().get();
        RadioButton one = new RadioButton(selected == null ? "Only the selected instance" : "Only \"" + selected.name() + "\"");
        one.setToggleGroup(scope);
        one.setDisable(selected == null);
        HBox scopeRow = new HBox(18, all, one);

        Button apply = new Button("Apply changes");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(event -> apply(plan, one.isSelected() && selected != null ? List.of(selected)
                : List.copyOf(store.instances())));
        Button notNow = new Button(mode == Mode.FIRST_RUN ? "No thanks" : "Not now");
        notNow.getStyleClass().add("secondary-button");
        notNow.setOnAction(event -> {
            if (mode == Mode.FIRST_RUN) {
                listener.declined();
            } else {
                showIntro();
            }
        });
        HBox buttons = new HBox(10, apply, notNow);

        getChildren().setAll(heading, found, changesTitle, changes, tips, scopeRow, buttons);
    }

    private static void addRow(GridPane grid, int row, String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("hardware-name");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("hardware-value");
        valueLabel.setWrapText(true);
        grid.addRow(row, nameLabel, valueLabel);
    }

    /** "a" or "an" before the tier name ("an Ultra tier PC"). */
    private static String article(Tier tier) {
        return tier == Tier.ULTRA ? "an" : "a";
    }

    // ================================================================== stage 4: apply / done

    private void apply(TorcherRig.Plan plan, List<Instance> targets) {
        try {
            List<String> versionIds = new ArrayList<>();
            for (Instance instance : targets) {
                MinecraftVersion version = instancePanel.resolveVersion(instance);
                versionIds.add(version != null ? version.id() : "unknown");
            }
            TorcherRig.apply(plan, targets, versionIds);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.error("TorcherRig couldn't apply its changes: " + e.getMessage());
            return;
        }
        Toast.info("TorcherRig set Nexus up for your " + plan.tier().label() + " tier PC.");

        Label title = new Label("All set!");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("Nexus is now set up for your " + plan.tier().label() + " tier PC. Your old "
                + "settings were backed up – press Revert to put them back. Instances that haven't been played "
                + "yet get their new settings when they first launch.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);

        Button done = new Button(mode == Mode.FIRST_RUN ? "Continue" : "Done");
        done.getStyleClass().add("primary-button");
        done.setOnAction(event -> {
            if (mode == Mode.SETTINGS) {
                showIntro();
            }
            listener.done();
        });
        Button revert = new Button("Revert");
        revert.getStyleClass().add("secondary-button");
        revert.setOnAction(event -> revert());
        getChildren().setAll(title, text, new HBox(10, done, revert));
    }

    private void revert() {
        try {
            TorcherRig.revert(List.copyOf(store.instances()));
            Toast.info("TorcherRig's changes were undone – your old settings are back.");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.error("Couldn't undo TorcherRig's changes: " + e.getMessage());
        }
        showIntro();
    }
}
