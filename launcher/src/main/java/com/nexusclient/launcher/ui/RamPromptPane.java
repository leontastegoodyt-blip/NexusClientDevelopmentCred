package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import com.nexusclient.launcher.torcherrig.TorcherRig;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.management.ManagementFactory;

/**
 * RamPromptPane – the manual memory (RAM) setting: how much of the PC's memory the game may use.
 * Shows the PC's total memory, a recommended amount, and a slider (the project brief, Launcher → First run).
 *
 * Used in the first-run setup (when the player says no to TorcherRig) and in Settings → Game.
 */
public class RamPromptPane extends VBox {

    /**
     * @param onSaved called after the player saves (can be null)
     * @param onSkip  if not null, a "Skip" button is shown that calls this
     */
    public RamPromptPane(Runnable onSaved, Runnable onSkip) {
        getStyleClass().add("torcher-pane");
        setSpacing(14);

        long totalMb = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize() / (1024 * 1024);
        int recommended = recommendedMb(totalMb);
        // The slider goes from 1 GB up to "all but 2 GB" (and never above 32 GB – Minecraft doesn't need more).
        int maxMb = (int) Math.max(2048, Math.min(totalMb - 2048, 32768));

        Label title = new Label("Game memory (RAM)");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("Your PC has " + TorcherRig.formatGb((int) totalMb) + " of memory. Minecraft needs "
                + "enough to run smoothly, but giving it too much leaves too little for Windows and makes things "
                + "slower. We recommend " + TorcherRig.formatGb(recommended) + ".");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);

        Settings settings = SettingsStore.get();
        Slider slider = new Slider(1024, maxMb, settings.ramMb > 0 ? Math.min(settings.ramMb, maxMb) : recommended);
        slider.setMajorTickUnit(1024);
        slider.setMinorTickCount(1);
        slider.setBlockIncrement(512);
        slider.setSnapToTicks(true); // jumps in 0.5 GB steps
        slider.setShowTickMarks(true);
        slider.getStyleClass().add("ram-slider");

        Label value = new Label();
        value.getStyleClass().add("ram-value");
        Label warning = new Label();
        warning.getStyleClass().add("torcher-tip");
        warning.setWrapText(true);
        Runnable update = () -> {
            int mb = (int) (Math.round(slider.getValue() / 512.0) * 512);
            value.setText(TorcherRig.formatGb(mb) + (mb == recommended ? "  (recommended)" : ""));
            warning.setText(mb > totalMb * 0.6
                    ? "That's a lot – it leaves little memory for Windows, which can make the game stutter."
                    : "");
        };
        slider.valueProperty().addListener((observable, oldValue, newValue) -> update.run());
        update.run();

        Button save = new Button("Save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            settings.ramMb = (int) (Math.round(slider.getValue() / 512.0) * 512);
            settings.optimisePromptPending = false;
            SettingsStore.save();
            Toast.info("The game will use " + TorcherRig.formatGb(settings.ramMb) + " of memory.");
            if (onSaved != null) {
                onSaved.run();
            }
        });
        Button reset = new Button("Use recommended");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(event -> slider.setValue(recommended));

        HBox buttons = new HBox(10, save, reset);
        buttons.setAlignment(Pos.CENTER_LEFT);
        if (onSkip != null) {
            Button skip = new Button("Skip");
            skip.getStyleClass().add("secondary-button");
            skip.setOnAction(event -> onSkip.run());
            buttons.getChildren().add(skip);
        }

        getChildren().addAll(title, text, value, slider, warning, buttons);
    }

    /** Half the PC's memory, between 2 and 4 GB, in 0.5 GB steps. */
    public static int recommendedMb(long totalMb) {
        long half = (totalMb / 2 / 512) * 512;
        return (int) Math.max(2048, Math.min(4096, half));
    }
}
