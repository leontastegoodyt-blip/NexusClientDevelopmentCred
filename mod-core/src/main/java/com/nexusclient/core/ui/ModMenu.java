package com.nexusclient.core.ui;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ShareCodes;
import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.module.NexusSettings;
import com.nexusclient.core.online.DeveloperMode;
import com.nexusclient.core.online.Social;
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
 * favourites, on/off toggles, and a settings page per mod). Laid out like Lunar Client's menu:
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │ ⬡ NEXUS   Mods  Edit HUD  Share  Settings            [ search ] │  header
 *   │ (All) (★ Favourites) (HUD) (PvP) (Visual) (Chat) (Utility)       │  category chips
 *   │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                     │
 *   │ │ [FP] │ │ [CP] │ │ [Zo] │ │ ...  │ │      │   mod cards: icon,   │
 *   │ │ FPS  │ │ CPS  │ │ Zoom │ │      │ │      │   name, and a big    │
 *   │ │ON/OFF│ │ON/OFF│ │ON/OFF│ │      │ │      │   ENABLED bar        │
 *   │ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘                     │
 *   │ the hovered mod's description …                 Esc back · RShift │  footer
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * Click a card's bar to switch the mod on/off, the ★ to favourite it, anywhere else (or right-click)
 * to open its settings page.
 *
 * How it's drawn: "immediate mode" – every frame, render() draws everything from scratch. While
 * drawing, each clickable thing also records its rectangle and what clicking it does (a "hit"), so
 * the click handler can never disagree with what's on screen.
 */
public class ModMenu implements NexusScreen {

    private enum View { GRID, SETTINGS, SHARE, SOCIAL }

    private static final String ALL = "All";
    private static final String FAVOURITES = "Favourites"; // shown with a drawn star in front

    /** Colour presets offered for colour settings. */
    private static final int[] PRESETS = {0xFF3AA8FF, 0xFF5FD3FF, 0xFF8B5CFF, 0xFFFF2BD6, 0xFFFFB547,
            0xFF3DE0A0, 0xFFFF5A5A, 0xFFFFFFFF, 0xFF101018};

    // Card and layout sizes (Minecraft "GUI pixels").
    private static final int CARD_MIN_W = 94;
    private static final int CARD_H = 76;
    private static final int GAP = 6;
    private static final int ROW_H = 22;

    // ------------------------------------------------------------------ state
    private View view = View.GRID;
    private boolean settingsFromTab;       // opened with the "Settings" tab (Back → grid)
    private String category = ALL;
    private String search = "";
    private boolean searchFocused;
    private double scroll;
    private double maxScroll;
    private Module selected;
    private TextSetting focusedText;
    private NumberSetting draggingSlider;
    private int sliderX1, sliderX2;
    private String shareInput = "";
    private boolean shareFocused;
    private String shareMessage = "";
    private String socialInput = "";
    private boolean socialFocused;
    private String socialChannel = "Party";
    private String footer = "";
    private final long openedAt = System.currentTimeMillis();

    // The layout, worked out each frame.
    private int px, py, pw, ph;
    private int contentX, contentY, contentW, contentH;

    /** Something clickable on screen: where it is and what clicking it does. */
    private interface Click {
        void run(double mx, double my, int button);
    }

    private static final class Hit {
        final int x1, y1, x2, y2;
        final boolean inContent; // only clickable where the scrolling area is visible
        final Click click;

        Hit(int x1, int y1, int x2, int y2, boolean inContent, Click click) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.inContent = inContent;
            this.click = click;
        }
    }

    private final List<Hit> hits = new ArrayList<Hit>();

    public ModMenu() {}

    /** Opens the menu on the Social tab (friends, party, clan chat). */
    public static ModMenu social() {
        ModMenu menu = new ModMenu();
        menu.view = View.SOCIAL;
        return menu;
    }

    /** Opens the menu straight on a module's settings page (used by the HUD editor's right-click). */
    public static ModMenu forModule(Module module) {
        ModMenu menu = new ModMenu();
        menu.selected = module;
        menu.view = View.SETTINGS;
        return menu;
    }

    private void layout(Renderer r) {
        pw = Math.min(560, r.width() - 24);
        ph = Math.min(330, r.height() - 16);
        px = (r.width() - pw) / 2;
        py = (r.height() - ph) / 2;
        contentX = px + 12;
        contentY = py + (view == View.GRID ? 56 : 38);
        contentW = pw - 24;
        contentH = py + ph - 18 - contentY;
    }

    private void hit(int x1, int y1, int x2, int y2, Click click) {
        hits.add(new Hit(x1, y1, x2, y2, false, click));
    }

    private void contentHit(int x1, int y1, int x2, int y2, Click click) {
        hits.add(new Hit(x1, y1, x2, y2, true, click));
    }

    // ================================================================== drawing

    @Override
    public void render(Renderer r, int mouseX, int mouseY) {
        hits.clear();
        footer = "";
        layout(r);

        // Opening animation: the backdrop fades in and the panel glides up a few pixels (180 ms).
        double t = Math.min(1, (System.currentTimeMillis() - openedAt) / 180.0);
        double eased = 1 - Math.pow(1 - t, 3);
        r.rect(0, 0, r.width(), r.height(), ((int) (0xC0 * eased) << 24) | 0x05050A);
        int slide = (int) Math.round((1 - eased) * 8);
        r.push(0, slide, 1);

        // The panel: rounded, near-black, with a soft outer edge and the logo gradient along the top.
        UiKit.round(r, px - 1, py - 1, px + pw + 1, py + ph + 1, 8, 0x40000000);
        UiKit.roundBox(r, px, py, px + pw, py + ph, 7, 0xF20B0B12, 0xFF22223A);
        UiKit.gradientLine(r, px + 8, py, px + pw - 8, 0xE0);

        drawHeader(r, mouseX, mouseY);
        if (view == View.GRID) {
            drawChips(r, mouseX, mouseY);
        }

        r.enableClip(contentX, contentY, contentX + contentW, contentY + contentH);
        if (view == View.GRID) {
            drawGrid(r, mouseX, mouseY);
        } else if (view == View.SETTINGS) {
            drawSettings(r, mouseX, mouseY);
        } else if (view == View.SOCIAL) {
            drawSocial(r, mouseX, mouseY);
        } else {
            drawShare(r, mouseX, mouseY);
        }
        r.disableClip();
        drawScrollbar(r);
        drawFooter(r);
        r.pop();
    }

    // ------------------------------------------------------------------ header

    private void drawHeader(Renderer r, int mouseX, int mouseY) {
        UiKit.logo(r, px + 12, py + 7);
        r.text("NEXUS", px + 33, py + 11, 0xFFFFFFFF, true);
        int x = px + 33 + r.textWidth("NEXUS") + 16;

        // Tabs.
        String[] tabs = {"Mods", "Edit HUD", "Share", "Social", "Settings"};
        for (String tab : tabs) {
            int w = r.textWidth(tab) + 14;
            boolean active = (tab.equals("Mods") && view == View.GRID)
                    || (tab.equals("Share") && view == View.SHARE)
                    || (tab.equals("Social") && view == View.SOCIAL)
                    || (tab.equals("Settings") && view == View.SETTINGS && settingsFromTab);
            boolean hover = UiKit.inside(mouseX, mouseY, x, py + 6, x + w, py + 24);
            if (active) {
                UiKit.round(r, x, py + 6, x + w, py + 24, 4, UiKit.accent(0x26));
                r.rect(x + 5, py + 23, x + w - 5, py + 24, UiKit.accent());
            } else if (hover) {
                UiKit.round(r, x, py + 6, x + w, py + 24, 4, 0x14FFFFFF);
            }
            r.text(tab, x + 7, py + 11, active || hover ? UiKit.TEXT : UiKit.TEXT_DIM, false);
            final String name = tab;
            hit(x, py + 6, x + w, py + 24, (mx, my, button) -> openTab(name));
            x += w + 2;
        }

        // Search box (in the Mods view).
        if (view == View.GRID) {
            int sw = Math.min(130, Math.max(70, px + pw - 12 - x - 8));
            int sx = px + pw - 12 - sw;
            UiKit.roundBox(r, sx, py + 7, sx + sw, py + 23, 4, 0xFF08080E, searchFocused ? UiKit.accent() : 0xFF26263C);
            drawSearchGlass(r, sx + 6, py + 11, searchFocused ? UiKit.accent() : UiKit.TEXT_FAINT);
            String shown = search.isEmpty() && !searchFocused ? "Search mods" : search;
            shown = fitEnd(r, shown, sw - 24);
            r.text(shown, sx + 18, py + 11, search.isEmpty() && !searchFocused ? UiKit.TEXT_FAINT : UiKit.TEXT, false);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = sx + 18 + r.textWidth(shown);
                r.rect(cx, py + 10, cx + 1, py + 20, UiKit.accent());
            }
            hit(sx, py + 7, sx + sw, py + 23, (mx, my, button) -> searchFocused = true);
        }
        r.rect(px + 1, py + 30, px + pw - 1, py + 31, 0xFF17172A); // divider under the header
    }

    /** A tiny magnifying glass made of rectangles. */
    private static void drawSearchGlass(Renderer r, int x, int y, int colour) {
        r.outline(x, y, x + 6, y + 6, colour);
        r.rect(x + 6, y + 6, x + 7, y + 7, colour);
        r.rect(x + 7, y + 7, x + 8, y + 8, colour);
    }

    private void openTab(String tab) {
        searchFocused = false;
        scroll = 0;
        if (tab.equals("Mods")) {
            view = View.GRID;
            settingsFromTab = false;
        } else if (tab.equals("Edit HUD")) {
            NexusCore.get().save();
            NexusCore.get().platform().openScreen(new HudEditor());
        } else if (tab.equals("Share")) {
            view = View.SHARE;
        } else if (tab.equals("Social")) {
            view = View.SOCIAL;
        } else {
            selected = NexusCore.get().settings();
            view = View.SETTINGS;
            settingsFromTab = true;
        }
    }

    // ------------------------------------------------------------------ category chips

    private List<String> chips() {
        List<String> entries = new ArrayList<String>();
        entries.add(ALL);
        entries.add(FAVOURITES);
        for (Category c : Category.values()) {
            if (c == Category.DEVELOPER && !DeveloperMode.active()) {
                continue; // only the Nexus developer sees the Developer tools
            }
            entries.add(c.label());
        }
        return entries;
    }

    private void drawChips(Renderer r, int mouseX, int mouseY) {
        List<String> chips = chips();
        // Shrink the padding when the panel is narrow, so every chip fits on one line.
        int textTotal = 0;
        for (String chip : chips) {
            textTotal += chipWidth(r, chip);
        }
        int padding = Math.max(3, Math.min(9, (contentW - textTotal - (chips.size() - 1) * 4) / (chips.size() * 2)));
        int x = contentX;
        int y = py + 37;
        for (String chip : chips) {
            int w = chipWidth(r, chip) + padding * 2;
            boolean active = chip.equals(category);
            boolean hover = UiKit.inside(mouseX, mouseY, x, y, x + w, y + 14);
            int colour = chip.equals(ALL) || chip.equals(FAVOURITES) ? UiKit.accent() | 0xFF000000 : categoryColour(chip);
            if (active) {
                UiKit.roundBox(r, x, y, x + w, y + 14, 7, (0x30 << 24) | (colour & 0xFFFFFF), colour);
            } else {
                UiKit.roundBox(r, x, y, x + w, y + 14, 7, hover ? 0xFF1C1C2C : 0xFF121220, hover ? 0xFF34344E : 0xFF20203A);
            }
            int textColour = active ? 0xFFFFFFFF : (hover ? UiKit.TEXT : UiKit.TEXT_DIM);
            int tx = x + padding;
            if (chip.equals(FAVOURITES)) {
                UiKit.star(r, tx, y + 3, active ? 0xFFFFB547 : textColour);
                tx += 10;
            }
            r.text(chip, tx, y + 3, textColour, false);
            final String name = chip;
            hit(x, y, x + w, y + 14, (mx, my, button) -> {
                category = name;
                scroll = 0;
            });
            x += w + 4;
        }
    }

    /** A chip's text width (Favourites also has its star in front). */
    private static int chipWidth(Renderer r, String chip) {
        return r.textWidth(chip) + (chip.equals(FAVOURITES) ? 10 : 0);
    }

    /** Each category has its own colour (chips and the mods' icon tiles). */
    private static int categoryColour(String label) {
        if (label.equals(Category.HUD.label())) return 0xFF3AA8FF;
        if (label.equals(Category.PVP.label())) return 0xFFFF6B5E;
        if (label.equals(Category.VISUAL.label())) return 0xFF8B5CFF;
        if (label.equals(Category.CHAT.label())) return 0xFF3DE0A0;
        if (label.equals(Category.UTILITY.label())) return 0xFFFFB547;
        if (label.equals(Category.DEVELOPER.label())) return 0xFFFF2BD6;
        return 0xFF3AA8FF;
    }

    // ------------------------------------------------------------------ grid of mod cards

    /** The modules shown in the grid (by category, favourites and search). */
    private List<Module> visibleModules() {
        List<Module> list = new ArrayList<Module>();
        String query = search.toLowerCase(Locale.ROOT).trim();
        for (Module module : NexusCore.get().modules()) {
            if (module instanceof NexusSettings) {
                continue; // that's the "Settings" tab
            }
            if (module.category() == Category.DEVELOPER && !DeveloperMode.active()) {
                continue;
            }
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

    private void drawGrid(Renderer r, int mouseX, int mouseY) {
        List<Module> list = visibleModules();
        if (list.isEmpty()) {
            String message = category.equals(FAVOURITES) ? "No favourites yet – press the star on a mod." : "No mods match \"" + search + "\".";
            UiKit.centered(r, message, contentX + contentW / 2, contentY + 30, UiKit.TEXT_DIM, false);
            maxScroll = 0;
            return;
        }
        int columns = Math.max(2, (contentW + GAP) / (CARD_MIN_W + GAP));
        int cardW = (contentW - (columns - 1) * GAP) / columns;
        int rows = (list.size() + columns - 1) / columns;
        maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - contentH);
        scroll = Math.min(scroll, maxScroll);
        boolean mouseInContent = UiKit.inside(mouseX, mouseY, contentX, contentY, contentX + contentW, contentY + contentH);
        for (int i = 0; i < list.size(); i++) {
            int x = contentX + (i % columns) * (cardW + GAP);
            int y = contentY + (i / columns) * (CARD_H + GAP) - (int) scroll;
            if (y + CARD_H < contentY || y > contentY + contentH) {
                continue; // scrolled out of view
            }
            drawCard(r, list.get(i), x, y, cardW, mouseInContent ? mouseX : -1, mouseY);
        }
    }

    private void drawCard(Renderer r, final Module module, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = UiKit.inside(mouseX, mouseY, x, y, x + w, y + CARD_H);
        boolean on = module.isEnabled() && module.isSupported() && !module.isBlockedByServer();
        int colour = categoryColour(module.category().label());
        if (hover) {
            footer = module.description();
        }

        // The card.
        UiKit.roundBox(r, x, y, x + w, y + CARD_H, 5, hover ? 0xFF171726 : 0xFF11111B,
                on ? UiKit.accent(hover ? 0xD0 : 0x70) : (hover ? 0xFF38385A : 0xFF1F1F33));
        if (on) {
            r.rect(x + 6, y + 1, x + w - 6, y + 2, UiKit.accent(0x60)); // a lit edge on the top
        }

        // Favourite star (top left) and the "settings" dots (top right).
        boolean starHover = UiKit.inside(mouseX, mouseY, x + 3, y + 3, x + 14, y + 13);
        UiKit.star(r, x + 5, y + 5, module.isFavourite() ? 0xFFFFB547 : (starHover ? UiKit.TEXT_DIM : 0xFF33334C));
        for (int d = 0; d < 3; d++) {
            r.rect(x + w - 13 + d * 3, y + 7, x + w - 11 + d * 3, y + 9, hover ? UiKit.TEXT_DIM : 0xFF33334C);
        }

        // The icon tile: the mod's initials on a tile in its category's colour.
        int tile = 26;
        int tx = x + (w - tile) / 2;
        int ty = y + 9;
        UiKit.roundBox(r, tx, ty, tx + tile, ty + tile, 6, (on ? 0x34 : 0x1C) << 24 | (colour & 0xFFFFFF),
                (on ? 0xC0 : 0x60) << 24 | (colour & 0xFFFFFF));
        String initials = initials(module.name());
        // Whole-number sizes only – the pixel font looks uneven at sizes like 1.35x.
        float scale = initials.length() == 1 ? 2f : 1f;
        int textW = (int) (r.textWidth(initials) * scale);
        r.push(tx + (tile - textW) / 2f, ty + (tile - 8 * scale) / 2f + 1, scale);
        r.text(initials, 0, 0, on ? 0xFFFFFFFF : (0xD0 << 24 | (colour & 0xFFFFFF)), false);
        r.pop();

        // The name.
        UiKit.centered(r, UiKit.fit(r, module.name(), w - 10), x + w / 2, y + 41, on ? 0xFFFFFFFF : UiKit.TEXT, false);

        // The big status bar along the bottom (click it to switch the mod on/off).
        int bx1 = x + 4, by1 = y + CARD_H - 19, bx2 = x + w - 4, by2 = y + CARD_H - 4;
        boolean barHover = UiKit.inside(mouseX, mouseY, bx1, by1, bx2, by2);
        String label;
        int fill;
        int text;
        if (!module.isSupported()) {
            label = "SOON";
            fill = 0xFF181824;
            text = UiKit.TEXT_FAINT;
        } else if (module.isBlockedByServer()) {
            label = "BLOCKED";
            fill = 0x40FF6B5E;
            text = UiKit.DANGER;
        } else if (module.isEnabled()) {
            label = "ENABLED";
            fill = barHover ? (0xFF000000 | UiKit.mix(UiKit.accent() & 0xFFFFFF, 0xFFFFFF, 0.25)) : (UiKit.accent() | 0xFF000000);
            text = 0xFF06060A;
        } else {
            label = "DISABLED";
            fill = barHover ? 0xFF2C2A40 : 0xFF1E1D2C;
            text = barHover ? UiKit.TEXT : UiKit.TEXT_DIM;
        }
        UiKit.round(r, bx1, by1, bx2, by2, 4, fill);
        UiKit.centered(r, label, (bx1 + bx2) / 2, by1 + 4, text, false);

        // What clicking does.
        contentHit(x + 3, y + 3, x + 14, y + 13, (mx, my, button) -> {
            module.setFavourite(!module.isFavourite());
            NexusCore.get().save();
        });
        contentHit(bx1, by1, bx2, by2, (mx, my, button) -> {
            if (button == 1) {
                openSettings(module);
            } else if (module.isSupported() && !module.isBlockedByServer()) {
                module.toggle();
                NexusCore.get().save();
            }
        });
        contentHit(x, y, x + w, y + CARD_H, (mx, my, button) -> openSettings(module));
    }

    /** "Toggle Sprint" → "TS", "FPS" → "FPS", "Zoom" → "Z". */
    static String initials(String name) {
        String[] words = name.replace("(", " ").replace(")", " ").trim().split("[\\s\\-/]+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        String word = words[0];
        if (word.length() <= 3 || (word.length() <= 4 && word.equals(word.toUpperCase(Locale.ROOT)))) {
            return word.toUpperCase(Locale.ROOT);
        }
        return word.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void openSettings(Module module) {
        selected = module;
        view = View.SETTINGS;
        settingsFromTab = false;
        scroll = 0;
    }

    // ------------------------------------------------------------------ a mod's settings page

    private void drawSettings(Renderer r, int mouseX, int mouseY) {
        final Module module = selected;
        int x = contentX;
        int y = contentY - (int) scroll;
        int right = contentX + contentW;
        boolean nexusSettings = module instanceof NexusSettings;

        // Header: back button, icon tile, name + description, on/off switch.
        if (!settingsFromTab) {
            boolean backHover = UiKit.inside(mouseX, mouseY, x, y + 4, x + 44, y + 20);
            UiKit.roundBox(r, x, y + 4, x + 44, y + 20, 4, backHover ? 0xFF1C1C2C : 0xFF121220, backHover ? UiKit.accent(0xA0) : 0xFF26263C);
            r.text("< Back", x + 7, y + 8, backHover ? UiKit.TEXT : UiKit.TEXT_DIM, false);
            contentHit(x, y + 4, x + 44, y + 20, (mx, my, button) -> {
                view = View.GRID;
                scroll = 0;
            });
            x += 52;
        }
        int colour = nexusSettings ? (UiKit.accent() | 0xFF000000) : categoryColour(module.category().label());
        UiKit.roundBox(r, x, y, x + 24, y + 24, 6, 0x30 << 24 | (colour & 0xFFFFFF), 0xC0 << 24 | (colour & 0xFFFFFF));
        String initials = nexusSettings ? "N" : initials(module.name());
        UiKit.centered(r, initials, x + 12, y + 8, 0xFFFFFFFF, false);
        r.text(module.name(), x + 32, y + 3, 0xFFFFFFFF, true);
        r.text(UiKit.fit(r, module.description(), right - x - 70), x + 32, y + 15, UiKit.TEXT_DIM, false);
        if (module.isSupported() && !nexusSettings && !module.isBlockedByServer()) {
            boolean hover = UiKit.inside(mouseX, mouseY, right - 26, y + 6, right - 2, y + 18);
            UiKit.pillToggle(r, right - 24, y + 6, module.isEnabled(), hover);
            contentHit(right - 26, y + 4, right, y + 20, (mx, my, button) -> {
                module.toggle();
                NexusCore.get().save();
            });
        }
        y += 32;
        if (!module.isSupported()) {
            notice(r, x - (settingsFromTab ? 0 : 52), y, right, "Coming soon to this version of Nexus.", UiKit.accent());
            y += 18;
        }
        if (module.isBlockedByServer()) {
            notice(r, contentX, y, right, "This server doesn't allow this mod, so it's switched off here.", UiKit.DANGER);
            y += 18;
        }

        // One row per setting.
        x = contentX;
        for (Setting<?> setting : module.settings()) {
            drawSettingRow(r, setting, x, y, right, mouseX, mouseY);
            y += ROW_H + 3;
        }

        // Waypoints also list this world's waypoints: show/hide or delete each one.
        if (module instanceof com.nexusclient.core.map.Waypoints) {
            y = drawWaypointList(r, (com.nexusclient.core.map.Waypoints) module, x, y, right, mouseX, mouseY);
        }

        // Buttons at the end.
        y += 4;
        int bx = x;
        if (module instanceof HudModule) {
            int w = r.textWidth("Move it in the HUD editor") + 16;
            button(r, "Move it in the HUD editor", bx, y, bx + w, y + 16, mouseX, mouseY, true, (mx, my, b) -> {
                NexusCore.get().save();
                NexusCore.get().platform().openScreen(new HudEditor());
            });
            bx += w + 6;
        }
        int w = r.textWidth("Reset to defaults") + 16;
        button(r, "Reset to defaults", bx, y, bx + w, y + 16, mouseX, mouseY, false, (mx, my, b) -> {
            for (Setting<?> setting : module.settings()) {
                setting.reset();
                if (setting instanceof ColorSetting) {
                    ((ColorSetting) setting).setChroma(false);
                }
            }
            NexusCore.get().save();
        });
        y += 22;
        maxScroll = Math.max(0, y + (int) scroll - contentY - contentH);
    }

    /** The waypoints of the world you're in, each with a show/hide switch and a delete button. */
    private int drawWaypointList(Renderer r, final com.nexusclient.core.map.Waypoints waypoints, int x, int y, int right,
                                 int mouseX, int mouseY) {
        com.nexusclient.core.platform.GameInfo game = NexusCore.get().game();
        y += 6;
        r.text("Waypoints here", x, y, 0xFFFFFFFF, true);
        r.text("Press B in game to add one", x + r.textWidth("Waypoints here") + 8, y, UiKit.TEXT_FAINT, false);
        y += 14;
        if (!game.inWorld()) {
            r.text("Join a world to see its waypoints.", x, y, UiKit.TEXT_DIM, false);
            return y + 14;
        }
        final java.util.List<com.nexusclient.core.map.WaypointStore.Waypoint> list = waypoints.current(game);
        if (list.isEmpty()) {
            r.text("None yet.", x, y, UiKit.TEXT_DIM, false);
            return y + 14;
        }
        for (final com.nexusclient.core.map.WaypointStore.Waypoint waypoint : list) {
            boolean rowHover = UiKit.inside(mouseX, mouseY, x, y, right, y + ROW_H);
            UiKit.roundBox(r, x, y, right, y + ROW_H, 4, rowHover ? 0xFF151522 : 0xFF10101A, rowHover ? 0xFF2A2A44 : 0xFF1A1A2C);
            UiKit.round(r, x + 7, y + 7, x + 15, y + 15, 2, 0xFF000000 | waypoint.colour);
            r.text(UiKit.fit(r, waypoint.name, 110), x + 22, y + 7, UiKit.TEXT, false);
            double dx = waypoint.x - game.x();
            double dz = waypoint.z - game.z();
            String where = waypoint.x + " " + waypoint.y + " " + waypoint.z + "   " + Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
            r.text(where, x + 140, y + 7, UiKit.TEXT_DIM, false);
            final int end = right - 8;
            // Delete (×) and show/hide switch.
            boolean deleteHover = UiKit.inside(mouseX, mouseY, end - 12, y + 5, end, y + 17);
            UiKit.roundBox(r, end - 12, y + 5, end, y + 17, 3, deleteHover ? 0x40FF6B5E : 0xFF181826, deleteHover ? UiKit.DANGER : 0xFF2A2A40);
            r.text("x", end - 8, y + 7, deleteHover ? UiKit.DANGER : UiKit.TEXT_DIM, false);
            UiKit.pillToggle(r, end - 40, y + 5, waypoint.visible, rowHover);
            contentHit(end - 12, y + 5, end, y + 17, (mx, my, b) -> {
                list.remove(waypoint);
                waypoints.store().save();
            });
            contentHit(end - 42, y + 3, end - 16, y + 19, (mx, my, b) -> {
                waypoint.visible = !waypoint.visible;
                waypoints.store().save();
            });
            y += ROW_H + 3;
        }
        return y;
    }

    private static void notice(Renderer r, int x, int y, int right, String text, int colour) {
        UiKit.round(r, x, y, right, y + 14, 3, (0x18 << 24) | (colour & 0xFFFFFF));
        r.rect(x, y, x + 2, y + 14, colour | 0xFF000000);
        r.text(text, x + 8, y + 3, colour | 0xFF000000, false);
    }

    private void drawSettingRow(Renderer r, final Setting<?> setting, int x, int y, final int right, int mouseX, int mouseY) {
        boolean rowHover = UiKit.inside(mouseX, mouseY, x, y, right, y + ROW_H);
        UiKit.roundBox(r, x, y, right, y + ROW_H, 4, rowHover ? 0xFF151522 : 0xFF10101A, rowHover ? 0xFF2A2A44 : 0xFF1A1A2C);
        r.text(UiKit.fit(r, setting.name(), (right - x) / 2 - 10), x + 8, y + 7, UiKit.TEXT, false);
        final int cy = y + (ROW_H - 12) / 2;
        final int end = right - 8;
        if (setting instanceof BooleanSetting) {
            UiKit.pillToggle(r, end - 22, cy, ((BooleanSetting) setting).isOn(), rowHover);
            contentHit(x, y, right, y + ROW_H, (mx, my, b) -> {
                ((BooleanSetting) setting).toggle();
                NexusCore.get().save();
            });
        } else if (setting instanceof NumberSetting) {
            final NumberSetting number = (NumberSetting) setting;
            int sliderW = Math.min(140, (right - x) / 3);
            final int s1 = end - sliderW;
            UiKit.smoothSlider(r, s1, cy, end, number.fraction(), setting == draggingSlider);
            String value = number.display();
            UiKit.round(r, s1 - r.textWidth(value) - 14, cy, s1 - 6, cy + 12, 3, 0xFF1C1C2C);
            r.text(value, s1 - r.textWidth(value) - 10, cy + 2, UiKit.TEXT, false);
            contentHit(s1 - 4, y, end + 4, y + ROW_H, (mx, my, b) -> {
                draggingSlider = number;
                sliderX1 = s1;
                sliderX2 = end;
                number.setFraction((mx - s1) / (double) (end - s1));
            });
        } else if (setting instanceof ColorSetting) {
            final ColorSetting colour = (ColorSetting) setting;
            int chromaW = r.textWidth("Chroma") + 12;
            int sx = end - chromaW - 6 - PRESETS.length * 13;
            for (final int preset : PRESETS) {
                boolean current = (colour.get() & 0xFFFFFF) == (preset & 0xFFFFFF) && !colour.chroma();
                UiKit.roundBox(r, sx, cy, sx + 11, cy + 11, 3, preset, current ? 0xFFFFFFFF : 0xFF2A2A40);
                contentHit(sx, cy, sx + 11, cy + 11, (mx, my, b) -> {
                    int alpha = colour.get() >>> 24;
                    colour.set((alpha << 24) | (preset & 0xFFFFFF)); // keep its see-through-ness
                    colour.setChroma(false);
                    NexusCore.get().save();
                });
                sx += 13;
            }
            boolean chromaOn = colour.chroma();
            int c1 = end - chromaW;
            if (chromaOn) {
                // A rainbow edge for the chroma button.
                for (int i = 0; i < chromaW; i++) {
                    r.rect(c1 + i, cy - 1, c1 + i + 1, cy + 13, 0xFF000000 | java.awt.Color.HSBtoRGB(i / (float) chromaW, 0.7f, 1f));
                }
                UiKit.round(r, c1 + 1, cy, end - 1, cy + 12, 2, 0xFF10101A);
            } else {
                UiKit.roundBox(r, c1, cy, end, cy + 12, 3, 0xFF1A1A28, 0xFF2A2A40);
            }
            r.text("Chroma", c1 + 6, cy + 2, chromaOn ? 0xFFFFFFFF : UiKit.TEXT_DIM, false);
            contentHit(c1, cy, end, cy + 12, (mx, my, b) -> {
                colour.setChroma(!colour.chroma());
                NexusCore.get().save();
            });
        } else if (setting instanceof ChoiceSetting) {
            final ChoiceSetting choice = (ChoiceSetting) setting;
            int w = Math.max(80, r.textWidth(choice.get()) + 26);
            boolean hover = UiKit.inside(mouseX, mouseY, end - w, cy - 1, end, cy + 13);
            UiKit.roundBox(r, end - w, cy - 1, end, cy + 13, 3, hover ? 0xFF22223A : 0xFF181826, hover ? UiKit.accent(0xA0) : 0xFF2A2A40);
            r.text(choice.get(), end - w + 7, cy + 2, UiKit.TEXT, false);
            r.text(">", end - 9, cy + 2, UiKit.accent() | 0xFF000000, false);
            contentHit(end - w, cy - 1, end, cy + 13, (mx, my, b) -> {
                choice.next();
                NexusCore.get().save();
            });
        } else if (setting instanceof TextSetting) {
            final TextSetting text = (TextSetting) setting;
            int w = Math.min(160, (right - x) / 2);
            UiKit.textField(r, text.get(), "", end - w, cy - 2, end, cy + 14, setting == focusedText);
            contentHit(end - w, cy - 2, end, cy + 14, (mx, my, b) -> focusedText = text);
        }
    }

    /** A rounded button that records its own click. */
    private void button(Renderer r, String label, int x1, int y1, int x2, int y2, int mouseX, int mouseY,
                        boolean primary, Click click) {
        boolean hover = UiKit.inside(mouseX, mouseY, x1, y1, x2, y2);
        if (primary) {
            UiKit.round(r, x1, y1, x2, y2, 4, hover ? 0xFF000000 | UiKit.mix(UiKit.accent() & 0xFFFFFF, 0xFFFFFF, 0.2)
                    : UiKit.accent() | 0xFF000000);
            UiKit.centered(r, label, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2, 0xFF06060A, false);
        } else {
            UiKit.roundBox(r, x1, y1, x2, y2, 4, hover ? 0xFF1E1E2E : 0xFF14141F, hover ? UiKit.accent(0x90) : 0xFF26263C);
            UiKit.centered(r, label, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2, hover ? UiKit.TEXT : UiKit.TEXT_DIM, false);
        }
        contentHit(x1, y1, x2, y2, click);
    }

    // ------------------------------------------------------------------ share codes

    private void drawShare(Renderer r, int mouseX, int mouseY) {
        int x = contentX;
        int y = contentY - (int) scroll;
        int right = contentX + contentW;
        r.text("Share codes", x, y + 3, 0xFFFFFFFF, true);
        r.text("Send your crosshair or HUD layout to a friend as a short code.", x, y + 16, UiKit.TEXT_DIM, false);
        y += 32;

        int half = (contentW - 6) / 2;
        shareCard(r, x, y, x + half, "Crosshair", "Your crosshair's shape and colour", mouseX, mouseY, (mx, my, b) -> {
            ShareCodes.copy("CROSSHAIR", ShareCodes.crosshairCode(NexusCore.get().module("crosshair")),
                    message -> shareMessage = message);
        });
        shareCard(r, x + half + 6, y, right, "HUD layout", "Where all your HUD mods sit", mouseX, mouseY, (mx, my, b) -> {
            ShareCodes.copy("HUD", ShareCodes.hudCode(), message -> shareMessage = message);
        });
        y += 54;

        r.text("Got a code from a friend?", x, y, UiKit.TEXT, false);
        y += 12;
        UiKit.textField(r, shareInput, "NX-XXXX-XXXX, NXC-… or NXH-…", x, y, right - 116, y + 18, shareFocused);
        contentHit(x, y, right - 116, y + 18, (mx, my, b) -> shareFocused = true);
        button(r, "Paste", right - 110, y + 1, right - 62, y + 17, mouseX, mouseY, false, (mx, my, b) -> {
            String pasted = NexusCore.get().platform().clipboard();
            shareInput = pasted == null ? "" : pasted.trim();
        });
        button(r, "Use code", right - 56, y + 1, right, y + 17, mouseX, mouseY, true,
                (mx, my, b) -> ShareCodes.use(shareInput, message -> shareMessage = message));
        y += 24;
        if (!shareMessage.isEmpty()) {
            notice(r, x, y, right, shareMessage, UiKit.accent());
        }
        maxScroll = 0;
    }

    private void shareCard(Renderer r, int x1, int y, int x2, String title, String text, int mouseX, int mouseY, Click copy) {
        boolean hover = UiKit.inside(mouseX, mouseY, x1, y, x2, y + 46);
        UiKit.roundBox(r, x1, y, x2, y + 46, 5, hover ? 0xFF171726 : 0xFF11111B, hover ? UiKit.accent(0xA0) : 0xFF1F1F33);
        r.text(title, x1 + 8, y + 7, 0xFFFFFFFF, false);
        r.text(UiKit.fit(r, text, x2 - x1 - 16), x1 + 8, y + 18, UiKit.TEXT_DIM, false);
        r.text("Copy code >", x1 + 8, y + 32, UiKit.accent() | 0xFF000000, false);
        contentHit(x1, y, x2, y + 46, copy);
    }

    // ------------------------------------------------------------------ Social tab

    /**
     * Nexus Social (online/Social): on the left your status, invites, party and friends; on the right
     * the party or clan chat with a box to type in. The left side scrolls; the chat stays put.
     */
    private void drawSocial(Renderer r, int mouseX, int mouseY) {
        final Social social = NexusCore.get().get(Social.class);
        int split = contentX + Math.max(180, contentW * 46 / 100);
        int x = contentX;
        int right = split - 8;
        int y = contentY - (int) scroll;

        // --- Status ---
        boolean online = social.online();
        int dot = online ? 0xFF5BE49B : social.isActive() ? 0xFFFFB547 : 0xFF6A6A80;
        UiKit.round(r, x, y + 4, x + 6, y + 10, 3, dot);
        String me = NexusCore.get().game().playerName();
        r.text(online ? me : social.status(), x + 10, y + 3, online ? 0xFFFFFFFF : UiKit.TEXT_DIM, false);
        if (social.inClan()) {
            String clan = "[" + social.clanTag() + "] " + social.clanName();
            r.text(UiKit.fit(r, clan, right - x - 80), right - r.textWidth(UiKit.fit(r, clan, right - x - 80)), y + 3,
                    0xFF5FD3FF, false);
        }
        y += 18;
        if (!social.isActive()) {
            notice(r, x, y, right, "Nexus Social is switched off.", UiKit.TEXT_DIM);
            y += 22;
            button(r, "Switch on", x, y, x + 70, y + 16, mouseX, mouseY, true, (mx, my, b) -> {
                social.setEnabled(true);
                NexusCore.get().save();
            });
            y += 24;
        }

        // --- Invites and friend requests ---
        List<Social.Invite> invites = social.invites();
        List<Social.Person> requests = social.requests();
        if (!invites.isEmpty() || !requests.isEmpty()) {
            y = sectionTitle(r, "Invites", invites.size() + requests.size(), x, y, right);
            for (final Social.Invite invite : invites) {
                y = socialRow(r, x, y, right, 0xFFB98CFF, invite.fromName, "Party invite", mouseX, mouseY,
                        "Join", (mx, my, b) -> social.acceptInvite(invite),
                        "x", (mx, my, b) -> social.declineInvite(invite));
            }
            for (final Social.Person request : requests) {
                y = socialRow(r, x, y, right, 0xFF5BE49B, request.name, "Friend request", mouseX, mouseY,
                        "Accept", (mx, my, b) -> social.acceptFriend(request),
                        "x", (mx, my, b) -> social.declineFriend(request));
            }
            y += 4;
        }

        // --- Party ---
        List<Social.Person> party = social.partyMembers();
        y = sectionTitle(r, "Party", party.size(), x, y, right);
        if (party.isEmpty()) {
            r.text("Not in a party. Invite an online friend below.", x + 2, y + 2, UiKit.TEXT_FAINT, false);
            y += 16;
        } else {
            for (Social.Person member : party) {
                boolean leader = member.uuid.equals(social.partyLeader());
                String where = member.online ? (member.server.isEmpty() ? "Online" : member.server) : "Offline";
                y = socialRow(r, x, y, right, member.online ? 0xFF5BE49B : 0xFF4A4A60,
                        member.name + (leader ? "  (leader)" : ""), where, mouseX, mouseY,
                        null, null, null, null);
            }
            button(r, "Leave party", x, y + 2, x + 72, y + 16, mouseX, mouseY, false, (mx, my, b) -> social.leaveParty());
            y += 22;
        }

        // --- Friends ---
        List<Social.Person> friends = social.friends();
        int onlineCount = 0;
        for (Social.Person friend : friends) {
            if (friend.online) {
                onlineCount++;
            }
        }
        y = sectionTitle(r, "Friends", onlineCount, x, y, right);
        if (friends.isEmpty()) {
            r.text(online ? "No friends yet - add them in the launcher." : "Friends show up once you're online.",
                    x + 2, y + 2, UiKit.TEXT_FAINT, false);
            y += 16;
        }
        java.util.Set<java.util.UUID> inParty = new java.util.HashSet<java.util.UUID>();
        for (Social.Person member : party) {
            inParty.add(member.uuid);
        }
        for (final Social.Person friend : friends) {
            String where = friend.online ? (friend.server.isEmpty() ? "Online" : friend.server) : "Offline";
            y = socialRow(r, x, y, right, friend.online ? 0xFF5BE49B : 0xFF4A4A60, friend.name, where, mouseX, mouseY,
                    friend.online && !inParty.contains(friend.uuid) ? "Invite" : null,
                    (mx, my, b) -> social.inviteToParty(friend), null, null);
        }
        maxScroll = Math.max(0, y + (int) scroll - contentY - contentH + 8);

        drawSocialChat(r, social, split, mouseX, mouseY);
    }

    /** "PARTY · 3" with a thin line. Returns the y below it. */
    private static int sectionTitle(Renderer r, String title, int count, int x, int y, int right) {
        String text = title.toUpperCase(Locale.ROOT);
        r.text(text, x, y, UiKit.TEXT_DIM, false);
        int after = x + r.textWidth(text) + 6;
        if (count > 0) {
            String n = String.valueOf(count);
            UiKit.round(r, after, y - 1, after + r.textWidth(n) + 8, y + 9, 3, UiKit.accent(0x30));
            r.text(n, after + 4, y, UiKit.accent() | 0xFF000000, false);
            after += r.textWidth(n) + 12;
        }
        r.rect(after, y + 4, right, y + 5, 0xFF1C1C2C);
        return y + 13;
    }

    /** One person: a status dot, name, a dim second line, and up to two small buttons. */
    private int socialRow(Renderer r, int x, int y, int right, int dotColour, String name, String detail,
                          int mouseX, int mouseY, String action, Click onAction, String second, Click onSecond) {
        boolean hover = UiKit.inside(mouseX, mouseY, x, y, right, y + 22);
        UiKit.round(r, x, y, right, y + 22, 4, hover ? 0xFF15151F : 0xFF101018);
        UiKit.round(r, x + 6, y + 8, x + 12, y + 14, 3, dotColour);
        int buttons = (action != null ? r.textWidth(action) + 14 : 0) + (second != null ? 18 : 0);
        r.text(UiKit.fit(r, name, right - x - 26 - buttons), x + 17, y + 3, UiKit.TEXT, false);
        if (detail != null && !detail.isEmpty()) {
            r.text(UiKit.fit(r, detail, right - x - 26 - buttons), x + 17, y + 12, UiKit.TEXT_FAINT, false);
        }
        int bx = right - 3;
        if (second != null) {
            button(r, second, bx - 14, y + 4, bx, y + 18, mouseX, mouseY, false, onSecond);
            bx -= 18;
        }
        if (action != null) {
            int w = r.textWidth(action) + 10;
            button(r, action, bx - w, y + 4, bx, y + 18, mouseX, mouseY, true, onAction);
        }
        return y + 25;
    }

    /** The chat panel on the right: Party/Clan switch, the latest messages and a box to type in. */
    private void drawSocialChat(Renderer r, final Social social, int x1, int mouseX, int mouseY) {
        int x2 = contentX + contentW;
        int y1 = contentY;
        int y2 = contentY + contentH;
        UiKit.roundBox(r, x1, y1, x2, y2, 6, 0xFF0C0C14, 0xFF1F1F33);

        // Party | Clan switch.
        int tx = x1 + 6;
        for (final String channel : new String[] {"Party", "Clan"}) {
            boolean active = socialChannel.equals(channel);
            int w = r.textWidth(channel) + 14;
            UiKit.round(r, tx, y1 + 5, tx + w, y1 + 19, 4, active ? UiKit.accent(0x30) : 0x10FFFFFF);
            r.text(channel, tx + 7, y1 + 8, active ? UiKit.accent() | 0xFF000000 : UiKit.TEXT_DIM, false);
            hit(tx, y1 + 5, tx + w, y1 + 19, (mx, my, b) -> socialChannel = channel);
            tx += w + 4;
        }
        String hint = socialChannel.equals("Party") ? "or type @p in chat" : "or type @c in chat";
        r.text(hint, x2 - 6 - r.textWidth(hint), y1 + 8, UiKit.TEXT_FAINT, false);
        r.rect(x1 + 1, y1 + 24, x2 - 1, y1 + 25, 0xFF1C1C2C);

        // Messages: newest at the bottom, as many as fit.
        List<Social.ChatLine> lines = socialChannel.equals("Party") ? social.partyChat() : social.clanChat();
        int boxTop = y2 - 26;
        int ly = boxTop - 12;
        int width = x2 - x1 - 14;
        if (lines.isEmpty()) {
            String empty = socialChannel.equals("Party")
                    ? (social.inParty() ? "No party messages yet." : "Join a party to chat with it.")
                    : (social.inClan() ? "No clan messages yet." : "Join a clan in the launcher to chat with it.");
            UiKit.centered(r, empty, (x1 + x2) / 2, (y1 + 25 + boxTop) / 2 - 4, UiKit.TEXT_FAINT, false);
        }
        for (int i = lines.size() - 1; i >= 0 && ly > y1 + 28; i--) {
            Social.ChatLine line = lines.get(i);
            String name = line.from + ": ";
            r.text(name, x1 + 7, ly, socialChannel.equals("Party") ? 0xFFB98CFF : 0xFF5FD3FF, false);
            r.text(UiKit.fit(r, line.text, width - r.textWidth(name)), x1 + 7 + r.textWidth(name), ly, UiKit.TEXT, false);
            ly -= 11;
        }

        // Typing box + Send.
        UiKit.textField(r, socialInput, "Message your " + socialChannel.toLowerCase(Locale.ROOT) + "…",
                x1 + 5, boxTop, x2 - 50, boxTop + 18, socialFocused);
        hit(x1 + 5, boxTop, x2 - 50, boxTop + 18, (mx, my, b) -> socialFocused = true);
        boolean hover = UiKit.inside(mouseX, mouseY, x2 - 45, boxTop + 1, x2 - 5, boxTop + 17);
        UiKit.round(r, x2 - 45, boxTop + 1, x2 - 5, boxTop + 17, 4, hover
                ? 0xFF000000 | UiKit.mix(UiKit.accent() & 0xFFFFFF, 0xFFFFFF, 0.2) : UiKit.accent() | 0xFF000000);
        UiKit.centered(r, "Send", x2 - 25, boxTop + 5, 0xFF06060A, false);
        hit(x2 - 45, boxTop + 1, x2 - 5, boxTop + 17, (mx, my, b) -> sendSocial());
    }

    private void sendSocial() {
        String text = socialInput.trim();
        if (text.isEmpty()) {
            return;
        }
        Social social = NexusCore.get().get(Social.class);
        if (socialChannel.equals("Party")) {
            social.sendPartyChat(text);
        } else {
            social.sendClanChat(text);
        }
        socialInput = "";
    }

    // ------------------------------------------------------------------ scrollbar and footer

    private void drawScrollbar(Renderer r) {
        if (maxScroll <= 0) {
            return;
        }
        int trackX = contentX + contentW + 4;
        int barH = Math.max(16, (int) (contentH * contentH / (double) (contentH + maxScroll)));
        int barY = contentY + (int) ((contentH - barH) * (scroll / maxScroll));
        UiKit.round(r, trackX, contentY, trackX + 2, contentY + contentH, 1, 0x14FFFFFF);
        UiKit.round(r, trackX, barY, trackX + 2, barY + barH, 1, UiKit.accent(0xC0));
    }

    private void drawFooter(Renderer r) {
        int y = py + ph - 13;
        r.rect(px + 1, y - 4, px + pw - 1, y - 3, 0xFF17172A);
        String hints = view == View.GRID ? "Esc or Right Shift to close" : "Esc back";
        r.text(hints, px + pw - 12 - r.textWidth(hints), y, UiKit.TEXT_FAINT, false);
        String left = footer.isEmpty() ? "Nexus Client" : footer;
        r.text(UiKit.fit(r, left, pw - 40 - r.textWidth(hints)), px + 12, y,
                footer.isEmpty() ? UiKit.TEXT_FAINT : UiKit.TEXT_DIM, false);
    }

    /** Cuts the start of text so its end (what you're typing) fits. */
    private static String fitEnd(Renderer r, String text, int maxWidth) {
        String shown = text;
        while (r.textWidth(shown) > maxWidth && shown.length() > 1) {
            shown = shown.substring(1);
        }
        return shown;
    }

    // ================================================================== clicks

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        searchFocused = false;
        focusedText = null;
        shareFocused = false;
        socialFocused = false;
        boolean inContent = UiKit.inside(mx, my, contentX, contentY, contentX + contentW, contentY + contentH);
        // The newest hits are on top (e.g. the ★ on a card), so check those first.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if ((!hit.inContent || inContent) && UiKit.inside(mx, my, hit.x1, hit.y1, hit.x2, hit.y2)) {
                hit.click.run(mx, my, button);
                return true;
            }
        }
        return UiKit.inside(mx, my, px, py, px + pw, py + ph);
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
        scroll = Math.max(0, Math.min(maxScroll, scroll - amount * 18));
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
            } else if (socialFocused && !socialInput.isEmpty()) {
                socialInput = socialInput.substring(0, socialInput.length() - 1);
            } else if (focusedText != null && !focusedText.get().isEmpty()) {
                focusedText.set(focusedText.get().substring(0, focusedText.get().length() - 1));
            }
            return true;
        }
        if (key == Keys.ENTER) {
            if (socialFocused) {
                sendSocial(); // Enter sends the message (and keeps the box ready for the next)
                return true;
            }
            searchFocused = false;
            shareFocused = false;
            if (focusedText != null) {
                focusedText = null;
                NexusCore.get().save();
            }
            return true;
        }
        if (key == Keys.ESCAPE) {
            if (searchFocused || shareFocused || socialFocused || focusedText != null) {
                searchFocused = false;
                shareFocused = false;
                socialFocused = false;
                focusedText = null;
                return true;
            }
            if (view != View.GRID) {
                view = View.GRID;
                settingsFromTab = false;
                scroll = 0;
                return true;
            }
            NexusCore.get().platform().closeScreen();
            return true;
        }
        if (key == Keys.RIGHT_SHIFT && !searchFocused && !shareFocused && !socialFocused && focusedText == null) {
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
        if (socialFocused) {
            if (socialInput.length() < 256) {
                socialInput += c;
            }
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
