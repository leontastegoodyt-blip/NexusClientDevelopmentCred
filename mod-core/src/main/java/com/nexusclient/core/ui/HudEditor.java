package com.nexusclient.core.ui;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.util.Keys;

import java.util.ArrayList;
import java.util.List;

/**
 * HudEditor – move and resize HUD elements by hand (the project brief: drag-and-drop HUD editor: move,
 * scale, snap to edges…).
 *
 *   drag            move an element (it snaps to screen edges, the centre, and other elements)
 *   scroll wheel    make the element under the mouse bigger or smaller
 *   right-click     open that element's settings (colours, background, text shadow, chroma)
 */
public class HudEditor implements NexusScreen {

    private static final int SNAP_DISTANCE = 5;

    private HudModule dragging;
    private double grabOffsetX;
    private double grabOffsetY;

    // Guide lines shown while snapping (-1 = none).
    private int guideX = -1;
    private int guideY = -1;

    private Renderer lastRenderer;

    @Override
    public void render(Renderer r, int mouseX, int mouseY) {
        r = com.nexusclient.core.util.I18n.wrap(r); // text in the player's language
        lastRenderer = r;
        r.rect(0, 0, r.width(), r.height(), 0x70050508);
        // Faint centre lines to help line things up.
        r.rect(r.width() / 2, 0, r.width() / 2 + 1, r.height(), 0x14FFFFFF);
        r.rect(0, r.height() / 2, r.width(), r.height() / 2 + 1, 0x14FFFFFF);

        boolean preview = !NexusCore.get().game().inWorld();
        HudModule hovered = null;
        for (HudModule module : enabledModules()) {
            module.render(r, NexusCore.get().game(), preview);
            int x = module.pixelX(r);
            int y = module.pixelY(r);
            boolean hover = module == dragging
                    || (dragging == null && UiKit.inside(mouseX, mouseY, x, y, x + module.width(), y + module.height()));
            if (hover) {
                hovered = module;
                r.outline(x - 2, y - 2, x + module.width() + 2, y + module.height() + 2, UiKit.accent());
                r.rect(x - 2, y - 2, x + module.width() + 2, y + module.height() + 2, UiKit.accent(0x18));
            } else {
                dashedOutline(r, x - 1, y - 1, x + module.width() + 1, y + module.height() + 1, UiKit.accent(0x70));
            }
        }
        if (guideX >= 0) r.rect(guideX, 0, guideX + 1, r.height(), UiKit.accent(0xC0));
        if (guideY >= 0) r.rect(0, guideY, r.width(), guideY + 1, UiKit.accent(0xC0));

        // A name tag above the element under the mouse: "Keystrokes · 1.0x".
        if (hovered != null) {
            String label = hovered.name() + "  " + hovered.scale.display();
            int lw = r.textWidth(label) + 10;
            int lx = Math.max(2, Math.min(hovered.pixelX(r), r.width() - lw - 2));
            int ly = hovered.pixelY(r) - 15;
            if (ly < 2) {
                ly = hovered.pixelY(r) + hovered.height() + 4; // no room above – show it below
            }
            UiKit.roundBox(r, lx, ly, lx + lw, ly + 12, 4, 0xF0101018, UiKit.accent());
            r.text(label, lx + 5, ly + 2, UiKit.TEXT, false);
        }

        // The control panel in the middle (hidden while dragging, so it never gets in the way).
        if (dragging == null) {
            drawPanel(r, mouseX, mouseY);
        }
        UiKit.centered(r, "Drag to move  ·  Scroll to resize  ·  Right-click for settings", r.width() / 2,
                r.height() - 14, UiKit.TEXT_DIM, true);
    }

    // The panel's buttons: x1, y1, x2, y2 (worked out in drawPanel, used by mouseClicked).
    private int[] modsButton = new int[4];
    private int[] snapButton = new int[4];
    private int[] resetButton = new int[4];
    private int[] doneButton = new int[4];

    /**
     * The floating panel in the centre, like Lunar Client's:
     *   ┌───────────────────────────┐
     *   │   ⬡  [      MODS      ]   │
     *   │  (Snap: On) (Reset) (Done)│
     *   └───────────────────────────┘
     */
    private void drawPanel(Renderer r, int mouseX, int mouseY) {
        int w = 172;
        int h = 52;
        int x = (r.width() - w) / 2;
        int y = (r.height() - h) / 2;
        UiKit.round(r, x - 1, y - 1, x + w + 1, y + h + 1, 8, 0x50000000);
        UiKit.roundBox(r, x, y, x + w, y + h, 7, 0xF20B0B12, 0xFF26263E);
        UiKit.gradientLine(r, x + 8, y, x + w - 8, 0xE0);

        UiKit.logo(r, x + 9, y + 7);
        modsButton = new int[] {x + 31, y + 6, x + w - 8, y + 24};
        boolean modsHover = UiKit.inside(mouseX, mouseY, modsButton[0], modsButton[1], modsButton[2], modsButton[3]);
        UiKit.round(r, modsButton[0], modsButton[1], modsButton[2], modsButton[3], 4,
                modsHover ? 0xFF000000 | UiKit.mix(UiKit.accent() & 0xFFFFFF, 0xFFFFFF, 0.2) : UiKit.accent() | 0xFF000000);
        UiKit.centered(r, "MODS", (modsButton[0] + modsButton[2]) / 2, y + 11, 0xFF06060A, false);

        boolean snapping = NexusCore.get().settings().snapping.isOn();
        int by = y + 30;
        int third = (w - 16 - 8) / 3;
        snapButton = new int[] {x + 8, by, x + 8 + third, by + 15};
        resetButton = new int[] {x + 12 + third, by, x + 12 + third * 2, by + 15};
        doneButton = new int[] {x + 16 + third * 2, by, x + w - 8, by + 15};
        smallButton(r, snapping ? "Snap: On" : "Snap: Off", snapButton, mouseX, mouseY, snapping);
        smallButton(r, "Reset", resetButton, mouseX, mouseY, false);
        smallButton(r, "Done", doneButton, mouseX, mouseY, false);
    }

    private static void smallButton(Renderer r, String label, int[] b, int mouseX, int mouseY, boolean lit) {
        boolean hover = UiKit.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
        UiKit.roundBox(r, b[0], b[1], b[2], b[3], 4, lit ? UiKit.accent(0x30) : (hover ? 0xFF1E1E2E : 0xFF14141F),
                lit ? UiKit.accent() : (hover ? UiKit.accent(0x90) : 0xFF2A2A40));
        UiKit.centered(r, label, (b[0] + b[2]) / 2, b[1] + 4, lit || hover ? UiKit.TEXT : UiKit.TEXT_DIM, false);
    }

    /** A dotted border: 3 pixels on, 2 off. */
    private static void dashedOutline(Renderer r, int x1, int y1, int x2, int y2, int colour) {
        for (int x = x1; x < x2; x += 5) {
            r.rect(x, y1, Math.min(x + 3, x2), y1 + 1, colour);
            r.rect(x, y2 - 1, Math.min(x + 3, x2), y2, colour);
        }
        for (int y = y1; y < y2; y += 5) {
            r.rect(x1, y, x1 + 1, Math.min(y + 3, y2), colour);
            r.rect(x2 - 1, y, x2, Math.min(y + 3, y2), colour);
        }
    }

    private List<HudModule> enabledModules() {
        List<HudModule> list = new ArrayList<HudModule>();
        for (HudModule module : NexusCore.get().hudModules()) {
            if (module.category() == com.nexusclient.core.module.Category.DEVELOPER
                    && !com.nexusclient.core.online.DeveloperMode.active()) {
                continue; // the developer's own tools stay hidden for everyone else
            }
            if (module.isEnabled() && module.isSupported()) {
                list.add(module);
            }
        }
        return list;
    }

    /** The element under the mouse (the last-drawn one wins, as it's on top). */
    private HudModule moduleAt(double mx, double my) {
        HudModule found = null;
        for (HudModule module : enabledModules()) {
            int x = module.pixelX(lastRenderer);
            int y = module.pixelY(lastRenderer);
            if (UiKit.inside(mx, my, x, y, x + module.width(), y + module.height())) {
                found = module;
            }
        }
        return found;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (lastRenderer == null) {
            return false;
        }
        // The centre panel's buttons (worked out in drawPanel).
        if (inside(mx, my, doneButton)) {
            NexusCore.get().platform().closeScreen();
            return true;
        }
        if (inside(mx, my, snapButton)) {
            NexusCore.get().settings().snapping.toggle();
            return true;
        }
        if (inside(mx, my, modsButton)) {
            NexusCore.get().save();
            NexusCore.get().platform().openScreen(new ModMenu());
            return true;
        }
        if (inside(mx, my, resetButton)) {
            resetLayout();
            return true;
        }
        HudModule module = moduleAt(mx, my);
        if (module == null) {
            return false;
        }
        if (button == 1) {
            NexusCore.get().save();
            NexusCore.get().platform().openScreen(ModMenu.forModule(module));
            return true;
        }
        dragging = module;
        grabOffsetX = mx - module.pixelX(lastRenderer);
        grabOffsetY = my - module.pixelY(lastRenderer);
        return true;
    }

    private static boolean inside(double mx, double my, int[] b) {
        return UiKit.inside(mx, my, b[0], b[1], b[2], b[3]);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (dragging == null || lastRenderer == null) {
            return false;
        }
        Renderer r = lastRenderer;
        double x = mx - grabOffsetX;
        double y = my - grabOffsetY;
        int w = dragging.width();
        int h = dragging.height();
        guideX = -1;
        guideY = -1;
        if (NexusCore.get().settings().snapping.isOn()) {
            // Things to snap to: screen edges, the centre, and every other element's edges.
            List<int[]> xTargets = new ArrayList<int[]>(); // {line position, where the element's left edge would go}
            List<int[]> yTargets = new ArrayList<int[]>();
            xTargets.add(new int[] {0, 0});
            xTargets.add(new int[] {r.width(), r.width() - w});
            xTargets.add(new int[] {r.width() / 2, r.width() / 2 - w / 2});
            yTargets.add(new int[] {0, 0});
            yTargets.add(new int[] {r.height(), r.height() - h});
            yTargets.add(new int[] {r.height() / 2, r.height() / 2 - h / 2});
            for (HudModule other : enabledModules()) {
                if (other == dragging) continue;
                int ox = other.pixelX(r);
                int oy = other.pixelY(r);
                xTargets.add(new int[] {ox, ox});                        // line up left edges
                xTargets.add(new int[] {ox + other.width(), ox + other.width() - w}); // right edges
                yTargets.add(new int[] {oy + other.height() + 2, oy + other.height() + 2}); // stack below
                yTargets.add(new int[] {oy, oy});                        // line up tops
            }
            for (int[] target : xTargets) {
                if (Math.abs(x - target[1]) < SNAP_DISTANCE) {
                    x = target[1];
                    guideX = Math.min(target[0], r.width() - 1);
                    break;
                }
            }
            for (int[] target : yTargets) {
                if (Math.abs(y - target[1]) < SNAP_DISTANCE) {
                    y = target[1];
                    guideY = Math.min(target[0], r.height() - 1);
                    break;
                }
            }
        }
        x = Math.max(0, Math.min(x, r.width() - w));
        y = Math.max(0, Math.min(y, r.height() - h));
        dragging.setPosition(x / r.width(), y / r.height());
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            guideX = -1;
            guideY = -1;
            NexusCore.get().save();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        HudModule module = moduleAt(mx, my);
        if (module == null) {
            return false;
        }
        module.scale.set(module.scale.get() + (amount > 0 ? 0.1 : -0.1));
        NexusCore.get().save();
        return true;
    }

    /** Puts every HUD element back in a tidy column on the left. */
    private void resetLayout() {
        double y = 0.01;
        for (HudModule module : enabledModules()) {
            module.setPosition(0.005, y);
            module.scale.reset();
            y += (module.height() + 4.0) / Math.max(1, lastRenderer.height());
        }
        NexusCore.get().save();
    }

    @Override
    public boolean keyPressed(int key) {
        if (key == Keys.ESCAPE || key == Keys.RIGHT_SHIFT) {
            NexusCore.get().platform().closeScreen();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char character) {
        return false;
    }

    @Override
    public void onClose() {
        NexusCore.get().save();
    }
}
