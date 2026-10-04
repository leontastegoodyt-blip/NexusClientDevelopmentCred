package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.function.BooleanSupplier;

/**
 * WindowMemory – remembers where the launcher window was and how big it was, so it opens the same
 * way next time. If the saved spot isn't on any screen any more (e.g. a monitor was unplugged), the
 * window simply opens in the middle of the main screen.
 */
public final class WindowMemory {

    private WindowMemory() {}

    /** Puts the window where it was last time (call before showing it). */
    public static void restore(Stage stage) {
        Settings settings = SettingsStore.get();
        boolean saved = settings.windowWidth > 0 && settings.windowHeight > 0;
        boolean onAScreen = saved && !Screen.getScreensForRectangle(
                settings.windowX, settings.windowY, settings.windowWidth, settings.windowHeight).isEmpty();
        if (onAScreen) {
            stage.setX(settings.windowX);
            stage.setY(settings.windowY);
            stage.setWidth(Math.max(stage.getMinWidth(), settings.windowWidth));
            stage.setHeight(Math.max(stage.getMinHeight(), settings.windowHeight));
        } else {
            stage.centerOnScreen();
        }
    }

    /**
     * Saves the window's place and size whenever it closes or hides.
     *
     * @param maximized tells whether the window is maximised right now – then the normal size is
     *                  kept instead (so un-maximising next time goes back to a sensible size)
     */
    public static void track(Stage stage, BooleanSupplier maximized) {
        stage.setOnHiding(event -> {
            if (maximized.getAsBoolean() || stage.isIconified()) {
                return;
            }
            Settings settings = SettingsStore.get();
            settings.windowX = stage.getX();
            settings.windowY = stage.getY();
            settings.windowWidth = stage.getWidth();
            settings.windowHeight = stage.getHeight();
            SettingsStore.save();
        });
    }
}
