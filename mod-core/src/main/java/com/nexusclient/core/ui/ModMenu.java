package com.nexusclient.core.ui;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ShareCodes;
import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;
import com.nexusclient.core.settings.ColorSetting;
import com.nexusclient.core.settings.NumberSetting;
import com.nexusclient.core.settings.Setting;
import com.nexusclient.core.settings.TextSetting;
import com.nexusclient.core.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ModMenu – the in-game Nexus menu, opened with Right Shift (the project brief: search bar, categories,
 * favourites, on/off toggles, and a settings page per mod).
 *
 * It's drawn "immediate mode": every frame, render() draws everything from scratch based on the
 * current state, and the click handlers work out what was under the mouse using the same layout.
 */
public class ModMenu implements NexusScreen {

    private enum View { GRID, SETTINGS, SHARE }

    // The sidebar entries: "All", "Favourites", then each category.
    private static final String ALL = "All";
    private static final String FAVOURITES = "★ Favourites";

    /** Colour presets offered for colour settings. */
    private static final int[] PRESETS = {0xFF3AA8FF, 0xFFFF2BD6, 0xFF8B7BFF, 0xFFFFB547, 0xFF3DE0A0,
            0xFFFF5A5A, 0xFFFFFFFF, 0xFF101018};

    private View view = View.GRID;
    private String category = ALL;
    private String search = "";
    private boolean searchFocused = false;
    private double scroll = 0;
    private Module selected;
    private TextSetting focusedText;
    private NumberSetting draggingSlider;
    private String shareInput = "";
    private boolean shareFocused = false;
    private String shareMessage = "";

    // The layout, worked out each frame (so the click handlers can use it too).
    private int px, py, pw, ph;       // the panel
    private int contentX, contentY, contentW, contentH;
    private int sliderX1, sliderX2;   // the slider being dragged

    public ModMenu() {}

    /** Opens the menu straight on a module's settings page (used by the HUD editor's right-click). */
    public static ModMenu forModule(Module module) {
        ModMenu menu = new ModMenu();
        menu.selected = module;
        menu.view = View.SETTINGS;
        return menu;
    }

    private void layout(Renderer r) {
        pw = Math.min(470, r.width() - 20);
        ph = Math.min(290, r.height() - 20);
        px = (r.width() - pw) / 2;
        py = (r.height() - ph) / 2;
        contentX = px + 104;
        contentY = py + 32;
        contentW = pw - 112;
        contentH = ph - 40;
    }

    // ================================================================== drawing

    @Override
    public void render(Renderer r, int mouseX, int mouseY) {
        layout(r);
        r.rect(0, 0, r.width(), r.height(), UiKit.BACKDROP);
        UiKit.box(r, px, py, px + pw, py + ph, UiKit.PANEL, UiKit.BORDER);
        r.rect(px, py, px + pw, py + 1, UiKit.accent(0xA0)); // neon line along the top

        // Header: logo and search box.
        r.text("NEXUS", px + 12, py + 11, UiKit.accent(), true);
        r.text("CLIENT", px + 12 + r.textWidth("NEXUS") + 4, py + 11, UiKit.TEXT_DIM, false);
        if (view == View.GRID) {
            UiKit.textField(r, search, "Search mods…", px + pw - 160, py + 7, px + pw - 8, py + 23, searchFocused);
        }

        drawSidebar(r, mouseX, mouseY);

        r.enableClip(contentX, contentY, contentX + contentW, contentY + contentH);
        if (view == View.GRID) {
            drawGrid(r, mouseX, mouseY);
        } else if (view == View.SETTINGS) {
            drawSettings(r, mouseX, mouseY);
        } else {
            drawShare(r, mouseX, mouseY);
        }
        r.disableClip();
    }

    private List<String> sidebarEntries() {
        List<String> entries = new ArrayList<String>();
        entries.add(ALL);
        entries.add(FAVOURITES);
        for (Category c : Category.values()) {
            entries.add(c.label());
        }
        return entries;
    }

    private void drawSidebar(Renderer r, int mouseX, int mouseY) {
        int x = px + 8;
        int y = py + 32;
        for (String entry : sidebarEntries()) {
            boolean active = view == View.GRID && entry.equals(category);
            boolean hover = UiKit.inside(mouseX, mouseY, x, y, x + 88, y + 18);
            if (active || hover) {
                UiKit.box(r, x, y, x + 88, y + 18, active ? UiKit.accent(0x20) : UiKit.PANEL_3,
                        active ? UiKit.accent(0xB0) : UiKit.BORDER);
            }
            r.text(entry, x + 8, y + 5, active ? UiKit.TEXT : UiKit.TEXT_DIM, false);
            y += 21;
        }
        int bottom = py + ph - 8;
        UiKit.button(r, "Share codes", x, bottom - 38, x + 88, bottom - 21,
                UiKit.inside(mouseX, mouseY, x, bottom - 38, x + 88, bottom - 21), false);
        UiKit.button(r, "Edit HUD", x, bottom - 17, x + 88, bottom,
                UiKit.inside(mouseX, mouseY, x, bottom - 17, x + 88, bottom), true);
    }

    /** The modules shown in the grid (by category, favourites and search). */
    private List<Module> visibleModules() {
        List<Module> list = new ArrayList<Module>();
        String query = search.toLowerCase(Locale.ROOT).trim();
        for (Module module : NexusCore.get().modules()) {
            boolean inCategory = category.equals(ALL)
                    || (category.equals(FAVOURITES) && module.isFavourite())
                    || module.category().label().equals(category);
            boolean matches = query.isEmpty() || module.name().toLowerCase(Locale.ROOT).contains(query)
                    || module.description().toLowerCase(Locale.ROOT).contains(query);
            if (inCategory && matches) {
                list.add(module);
            }
        }
        return list;
    }

    private static final int CARD_H = 38;
    private static final int GAP = 6;

    private int cardWidth() {
        return (contentW - GAP) / 2;
    }

    private void drawGrid(Renderer r, int mouseX, int mouseY) {
        List<Module> list = visibleModules();
        if (list.isEmpty()) {
            r.text(category.equals(FAVOURITES) ? "No favourites yet – press ★ on a mod." : "No mods match your search.",
                    contentX + 4, contentY + 6, UiKit.TEXT_DIM, false);
            return;
        }
        int cw = cardWidth();
        for (int i = 0; i < list.size(); i++) {
            Module module = list.get(i);
            int x = contentX + (i % 2) * (cw + GAP);
            int y = contentY + (i / 2) * (CARD_H + GAP) - (int) scroll;
            if (y + CARD_H < contentY || y > contentY + contentH) {
                continue; // off screen
            }
            boolean hover = UiKit.inside(mouseX, mouseY, x, y, x + cw, y + CARD_H) && mouseY >= contentY;
            UiKit.box(r, x, y, x + cw, y + CARD_H, hover ? UiKit.PANEL_3 : UiKit.PANEL_2,
                    module.isActive() ? UiKit.accent(0x90) : UiKit.BORDER);
            r.text(UiKit.fit(r, module.name(), cw - 44), x + 7, y + 7, UiKit.TEXT, false);
            r.text(UiKit.fit(r, module.description(), cw - 14), x + 7, y + 21, UiKit.TEXT_FAINT, false);
            // Favourite star.
            r.text("★", x + cw - 38, y + 6, module.isFavourite() ? 0xFFFFB547 : UiKit.TEXT_FAINT, false);
            // On/off switch, or a badge if it can't be switched on here.
            if (!module.isSupported()) {
                r.text("Soon", x + cw - 26, y + 7, UiKit.TEXT_FAINT, false);
            } else if (module.isBlockedByServer()) {
                r.text("Blocked", x + cw - 38, y + 7, UiKit.DANGER, false);
            } else {
                UiKit.toggle(r, x + cw - 26, y + 6, module.isEnabled(), hover);
            }
        }
    }

    private void drawSettings(Renderer r, int mouseX, int mouseY) {
        Module module = selected;
        int x = contentX;
        int y = contentY - (int) scroll;
        UiKit.button(r, "< Back", x, y, x + 46, y + 16, UiKit.inside(mouseX, mouseY, x, y, x + 46, y + 16), false);
        r.text(module.name(), x + 54, y + 4, UiKit.TEXT, true);
        if (module.isSupported() && !(module instanceof com.nexusclient.core.module.NexusSettings)) {
            UiKit.toggle(r, contentX + contentW - 22, y + 3, module.isEnabled(), false);
        }
        y += 22;
        r.text(UiKit.fit(r, module.description(), contentW), x, y, UiKit.TEXT_DIM, false);
        y += 14;
        if (!module.isSupported()) {
            r.text("Coming soon to this version of Nexus.", x, y, UiKit.accent(), false);
            y += 14;
        }
        if (module.isBlockedByServer()) {
            r.text("This server doesn't allow this mod, so it's switched off here.", x, y, UiKit.DANGER, false);
            y += 14;
        }
        y += 4;
        for (Setting<?> setting : module.settings()) {
            drawSettingRow(r, setting, x, y, mouseX, mouseY);
            y += 22;
        }
        if (module instanceof HudModule) {
            UiKit.button(r, "Move it in the HUD editor", x, y + 4, x + 150, y + 20,
                    UiKit.inside(mouseX, mouseY, x, y + 4, x + 150, y + 20), true);
            y += 26;
        }
        UiKit.button(r, "Reset to defaults", x, y + 4, x + 110, y + 20,
                UiKit.inside(mouseX, mouseY, x, y + 4, x + 110, y + 20), false);
    }

    private void drawSettingRow(Renderer r, Setting<?> setting, int x, int y, int mouseX, int mouseY) {
        int right = contentX + contentW - 4;
        r.text(UiKit.fit(r, setting.name(), contentW / 2), x, y + 5, UiKit.TEXT, false);
        if (setting instanceof BooleanSetting) {
            UiKit.toggle(r, right - 20, y + 3, ((BooleanSetting) setting).isOn(),
                    UiKit.inside(mouseX, mouseY, right - 20, y, right, y + 16));
        } else if (setting instanceof NumberSetting) {
            NumberSetting number = (NumberSetting) setting;
            UiKit.slider(r, right - 100, y + 3, right, number.fraction(), setting == draggingSlider);
            String value = number.display();
            r.text(value, right - 106 - r.textWidth(value), y + 5, UiKit.TEXT_DIM, false);
        } else if (setting instanceof ColorSetting) {
            ColorSetting colour = (ColorSetting) setting;
            int sx = right - PRESETS.length * 12 - 48;
            for (int preset : PRESETS) {
                boolean current = (colour.get() & 0xFFFFFF) == (preset & 0xFFFFFF) && !colour.chroma();
                UiKit.box(r, sx, y + 3, sx + 10, y + 13, preset, current ? 0xFFFFFFFF : UiKit.BORDER);
                sx += 12;
            }
            UiKit.button(r, "Chroma", right - 44, y + 1, right, y + 15, colour.chroma(), colour.chroma());
        } else if (setting instanceof ChoiceSetting) {
            UiKit.button(r, ((ChoiceSetting) setting).get(), right - 96, y + 1, right, y + 15,
                    UiKit.inside(mouseX, mouseY, right - 96, y + 1, right, y + 15), false);
        } else if (setting instanceof TextSetting) {
            UiKit.textField(r, ((TextSetting) setting).get(), "", right - 150, y, right, y + 16, setting == focusedText);
        }
    }

    private void drawShare(Renderer r, int mouseX, int mouseY) {
        int x = contentX;
        int y = contentY - (int) scroll;
        r.text("Share codes", x, y + 2, UiKit.TEXT, true);
        y += 16;
        r.text("Share your crosshair or HUD layout with a code. Friends paste it below.", x, y, UiKit.TEXT_DIM, false);
        y += 18;
        UiKit.button(r, "Copy my crosshair code", x, y, x + 150, y + 16, UiKit.inside(mouseX, mouseY, x, y, x + 150, y + 16), true);
        y += 22;
        UiKit.button(r, "Copy my HUD layout code", x, y, x + 150, y + 16, UiKit.inside(mouseX, mouseY, x, y, x + 150, y + 16), true);
        y += 30;
        r.text("Paste a code:", x, y, UiKit.TEXT, false);
        y += 12;
        UiKit.textField(r, shareInput, "NXC-… or NXH-…", x, y, x + contentW - 70, y + 16, shareFocused);
        UiKit.button(r, "Import", x + contentW - 64, y, x + contentW - 4, y + 16,
                UiKit.inside(mouseX, mouseY, x + contentW - 64, y, x + contentW - 4, y + 16), false);
        UiKit.button(r, "Paste", x, y + 20, x + 50, y + 36, UiKit.inside(mouseX, mouseY, x, y + 20, x + 50, y + 36), false);
        if (!shareMessage.isEmpty()) {
            r.text(shareMessage, x + 58, y + 25, UiKit.accent(), false);
        }
    }

    // ================================================================== clicks

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        searchFocused = false;
        focusedText = null;
        shareFocused = false;

        // Sidebar
        int x = px + 8;
        int y = py + 32;
        for (String entry : sidebarEntries()) {
            if (UiKit.inside(mx, my, x, y, x + 88, y + 18)) {
                category = entry;
                view = View.GRID;
                scroll = 0;
                return true;
            }
            y += 21;
        }
        int bottom = py + ph - 8;
        if (UiKit.inside(mx, my, x, bottom - 38, x + 88, bottom - 21)) {
            view = View.SHARE;
            scroll = 0;
            return true;
        }
        if (UiKit.inside(mx, my, x, bottom - 17, x + 88, bottom)) {
            NexusCore.get().save();
            NexusCore.get().platform().openScreen(new HudEditor());
            return true;
        }
        if (view == View.GRID && UiKit.inside(mx, my, px + pw - 160, py + 7, px + pw - 8, py + 23)) {
            searchFocused = true;
            return true;
        }
        if (!UiKit.inside(mx, my, contentX, contentY, contentX + contentW, contentY + contentH)) {
            return false;
        }
        if (view == View.GRID) {
            return clickGrid(mx, my, button);
        }
        if (view == View.SETTINGS) {
            return clickSettings(mx, my);
        }
        return clickShare(mx, my);
    }

    private boolean clickGrid(double mx, double my, int button) {
        List<Module> list = visibleModules();
        int cw = cardWidth();
        for (int i = 0; i < list.size(); i++) {
            Module module = list.get(i);
            int x = contentX + (i % 2) * (cw + GAP);
            int y = contentY + (i / 2) * (CARD_H + GAP) - (int) scroll;
            if (!UiKit.inside(mx, my, x, y, x + cw, y + CARD_H)) {
                continue;
            }
            if (UiKit.inside(mx, my, x + cw - 40, y + 3, x + cw - 28, y + 16)) {
                module.setFavourite(!module.isFavourite());
            } else if (button == 0 && module.isSupported() && !module.isBlockedByServer()
                    && UiKit.inside(mx, my, x + cw - 28, y + 3, x + cw, y + 18)) {
                module.toggle();
            } else {
                selected = module;
                view = View.SETTINGS;
                scroll = 0;
            }
            NexusCore.get().save();
            return true;
        }
        return false;
    }

    private boolean clickSettings(double mx, double my) {
        Module module = selected;
        int x = contentX;
        int y = contentY - (int) scroll;
        if (UiKit.inside(mx, my, x, y, x + 46, y + 16)) {
            view = View.GRID;
            scroll = 0;
            return true;
        }
        if (module.isSupported() && UiKit.inside(mx, my, contentX + contentW - 24, y, contentX + contentW, y + 16)) {
            module.toggle();
            NexusCore.get().save();
            return true;
        }
        y += 22 + 14 + (module.isSupported() ? 0 : 14) + (module.isBlockedByServer() ? 14 : 0) + 4;
        int right = contentX + contentW - 4;
        for (Setting<?> setting : module.settings()) {
            if (my >= y && my < y + 22) {
                clickSetting(setting, mx, y, right);
                NexusCore.get().save();
                return true;
            }
            y += 22;
        }
        if (module instanceof HudModule) {
            if (UiKit.inside(mx, my, x, y + 4, x + 150, y + 20)) {
                NexusCore.get().platform().openScreen(new HudEditor());
                return true;
            }
            y += 26;
        }
        if (UiKit.inside(mx, my, x, y + 4, x + 110, y + 20)) {
            for (Setting<?> setting : module.settings()) {
                setting.reset();
                if (setting instanceof ColorSetting) {
                    ((ColorSetting) setting).setChroma(false);
                }
            }
            NexusCore.get().save();
            return true;
        }
        return false;
    }

    private void clickSetting(Setting<?> setting, double mx, int y, int right) {
        if (setting instanceof BooleanSetting && mx >= right - 24) {
            ((BooleanSetting) setting).toggle();
        } else if (setting instanceof NumberSetting && mx >= right - 104) {
            draggingSlider = (NumberSetting) setting;
            sliderX1 = right - 100;
            sliderX2 = right;
            draggingSlider.setFraction((mx - sliderX1) / (sliderX2 - sliderX1));
        } else if (setting instanceof ColorSetting) {
            ColorSetting colour = (ColorSetting) setting;
            if (mx >= right - 44) {
                colour.setChroma(!colour.chroma());
                return;
            }
            int sx = right - PRESETS.length * 12 - 48;
            for (int preset : PRESETS) {
                if (mx >= sx && mx < sx + 10) {
                    int alpha = colour.get() >>> 24;
                    colour.set((alpha << 24) | (preset & 0xFFFFFF)); // keep its see-through-ness
                    colour.setChroma(false);
                }
                sx += 12;
            }
        } else if (setting instanceof ChoiceSetting && mx >= right - 96) {
            ((ChoiceSetting) setting).next();
        } else if (setting instanceof TextSetting && mx >= right - 150) {
            focusedText = (TextSetting) setting;
        }
    }

    private boolean clickShare(double mx, double my) {
        int x = contentX;
        int y = contentY - (int) scroll + 16 + 18;
        if (UiKit.inside(mx, my, x, y, x + 150, y + 16)) {
            NexusCore.get().platform().copyToClipboard(ShareCodes.crosshairCode(NexusCore.get().module("crosshair")));
            shareMessage = "Crosshair code copied!";
            return true;
        }
        y += 22;
        if (UiKit.inside(mx, my, x, y, x + 150, y + 16)) {
            NexusCore.get().platform().copyToClipboard(ShareCodes.hudCode());
            shareMessage = "HUD layout code copied!";
            return true;
        }
        y += 30 + 12;
        if (UiKit.inside(mx, my, x, y, x + contentW - 70, y + 16)) {
            shareFocused = true;
            return true;
        }
        if (UiKit.inside(mx, my, x + contentW - 64, y, x + contentW - 4, y + 16)) {
            shareMessage = ShareCodes.apply(shareInput);
            return true;
        }
        if (UiKit.inside(mx, my, x, y + 20, x + 50, y + 36)) {
            String pasted = NexusCore.get().platform().clipboard();
            shareInput = pasted == null ? "" : pasted.trim();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (draggingSlider != null) {
            draggingSlider = null;
            NexusCore.get().save();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button) {
        if (draggingSlider != null) {
            draggingSlider.setFraction((x - sliderX1) / (sliderX2 - sliderX1));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        scroll = Math.max(0, scroll - amount * 14);
        return true;
    }

    // ================================================================== typing

    @Override
    public boolean keyPressed(int key) {
        if (key == Keys.BACKSPACE) {
            if (searchFocused && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            } else if (shareFocused && !shareInput.isEmpty()) {
                shareInput = shareInput.substring(0, shareInput.length() - 1);
            } else if (focusedText != null && !focusedText.get().isEmpty()) {
                focusedText.set(focusedText.get().substring(0, focusedText.get().length() - 1));
            }
            return true;
        }
        if (key == Keys.ENTER) {
            searchFocused = false;
            shareFocused = false;
            if (focusedText != null) {
                focusedText = null;
                NexusCore.get().save();
            }
            return true;
        }
        if (key == Keys.ESCAPE) {
            if (searchFocused || shareFocused || focusedText != null) {
                searchFocused = false;
                shareFocused = false;
                focusedText = null;
                return true;
            }
            if (view != View.GRID) {
                view = View.GRID;
                scroll = 0;
                return true;
            }
            NexusCore.get().platform().closeScreen();
            return true;
        }
        if (key == Keys.RIGHT_SHIFT && !searchFocused && !shareFocused && focusedText == null) {
            NexusCore.get().platform().closeScreen();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        if (c < 32) {
            return false;
        }
        if (searchFocused) {
            search += c;
            scroll = 0;
            return true;
        }
        if (shareFocused) {
            shareInput += c;
            return true;
        }
        if (focusedText != null) {
            focusedText.set(focusedText.get() + c);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        NexusCore.get().save();
    }
}
