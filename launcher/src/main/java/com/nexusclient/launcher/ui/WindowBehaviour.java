package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.settings.SettingsStore;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * WindowBehaviour – what the launcher window does while the game is running
 * (Settings → General, the project brief: minimise to tray, hide, or stay open):
 *
 *   STAY_OPEN        – nothing changes
 *   MINIMISE_TO_TRAY – the window hides, and a small Nexus icon appears in the system tray
 *                      (next to the clock) to bring it back
 *   HIDE             – the window hides completely until the game closes
 *
 * When the game closes, the window always comes back.
 */
public final class WindowBehaviour {

    private static Stage stage;
    private static TrayIcon trayIcon;
    private static boolean hidden = false;

    private WindowBehaviour() {}

    public static void install(Stage window) {
        stage = window;
    }

    /** Called when the game has started. */
    public static void gameStarted() {
        if (stage == null) {
            return;
        }
        switch (SettingsStore.get().launchBehaviour) {
            case "MINIMISE_TO_TRAY" -> {
                if (SystemTray.isSupported()) {
                    hideWindow();
                    EventQueue.invokeLater(WindowBehaviour::addTrayIcon);
                } else {
                    stage.setIconified(true); // no tray on this computer: just minimise
                }
            }
            case "HIDE" -> hideWindow();
            default -> { } // stay open
        }
    }

    /** Called when the game has closed: bring the window back. */
    public static void gameClosed() {
        if (stage == null) {
            return;
        }
        if (hidden) {
            showWindow();
        }
        if (stage.isIconified()) {
            stage.setIconified(false);
        }
    }

    private static void hideWindow() {
        // Normally JavaFX quits when its last window closes – keep running while hidden.
        Platform.setImplicitExit(false);
        hidden = true;
        stage.hide();
    }

    private static void showWindow() {
        hidden = false;
        stage.show();
        stage.toFront();
        Platform.setImplicitExit(true);
        EventQueue.invokeLater(WindowBehaviour::removeTrayIcon);
    }

    // ------------------------------------------------------------------ the tray icon (runs on AWT's thread)

    private static void addTrayIcon() {
        if (trayIcon != null) {
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open Nexus");
        open.addActionListener(event -> Platform.runLater(WindowBehaviour::showWindow));
        MenuItem quit = new MenuItem("Quit Nexus (the game keeps running)");
        quit.addActionListener(event -> Platform.runLater(Platform::exit));
        menu.add(open);
        menu.add(quit);

        trayIcon = new TrayIcon(trayPicture(), "Nexus Client", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> Platform.runLater(WindowBehaviour::showWindow)); // double-click
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
            Platform.runLater(WindowBehaviour::showWindow); // no tray after all: don't leave the window hidden
        }
    }

    private static void removeTrayIcon() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    /** A small "N" on a dark rounded square, drawn in code (so no picture file is needed). */
    private static BufferedImage trayPicture() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x10, 0x10, 0x1A));
        g.fillRoundRect(0, 0, 32, 32, 10, 10);
        g.setColor(new java.awt.Color(0x00, 0xE5, 0xFF));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("N", 8, 24);
        g.dispose();
        return image;
    }
}
