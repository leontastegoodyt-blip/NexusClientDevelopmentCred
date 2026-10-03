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
        lastRenderer = r;
        r.rect(0, 0, r.width(), r.height(), 0x70050508);
        // Faint centre lines to help line things up.
        r.rect(r.width() / 2, 0, r.width() / 2 + 1, r.height(), 0x18FFFFFF);
        r.rect(0, r.height() / 2, r.width(), r.height() / 2 + 1, 0x18FFFFFF);

        boolean preview = !NexusCore.get().game().inWorld();
        for (HudModule module : enabledModules()) {
            module.render(r, NexusCore.get().game(), preview);
            int x = module.pixelX(r);
            int y = module.pixelY(r);
            boolean hover = module == dragging || UiKit.inside(mouseX, mouseY, x, y, x + module.width(), y + module.height());
            r.outline(x - 1, y - 1, x + module.width() + 1, y + module.height() + 1,
                    hover ? UiKit.accent() : UiKit.accent(0x60));
            if (hover) {
                String label = module.name() + "  ·  " + module.scale.display();
                r.text(label, x, Math.max(0, y - r.lineHeight() - 3), UiKit.accent(), true);
            }
        }
        if (guideX >= 0) r.rect(guideX, 0, guideX + 1, r.height(), UiKit.accent(0xC0));
        if (guideY >= 0) r.rect(0, guideY, r.width(), guideY + 1, UiKit.accent(0xC0));

        // Top bar with the editor's buttons.
        int cx = r.width() / 2;
        int y = 6;
        boolean snapping = NexusCore.get().settings().snapping.isOn();
        UiKit.button(r, "Done", cx - 110, y, cx - 50, y + 16, UiKit.inside(mouseX, mouseY, cx - 110, y, cx - 50, y + 16), true);
        UiKit.button(r, snapping ? "Snap: On" : "Snap: Off", cx - 46, y, cx + 16, y + 16,
                UiKit.inside(mouseX, mouseY, cx - 46, y, cx + 16, y + 16), false);
        UiKit.button(r, "Mod menu", cx + 20, y, cx + 80, y + 16, UiKit.inside(mouseX, mouseY, cx + 20, y, cx + 80, y + 16), false);
        UiKit.button(r, "Reset", cx + 84, y, cx + 124, y + 16, UiKit.inside(mouseX, mouseY, cx + 84, y, cx + 124, y + 16), false);
        UiKit.centered(r, "Drag to move  ·  Scroll to resize  ·  Right-click for settings", cx, r.height() - 14,
                UiKit.TEXT_DIM, true);
    }

    private List<HudModule> enabledModules() {
        List<HudModule> list = new ArrayList<HudModule>();
        for (HudModule module : NexusCore.get().hudModules()) {
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
        int cx = lastRenderer.width() / 2;
        if (UiKit.inside(mx, my, cx - 110, 6, cx - 50, 22)) {
            NexusCore.get().platform().closeScreen();
            return true;
        }
        if (UiKit.inside(mx, my, cx - 46, 6, cx + 16, 22)) {
            NexusCore.get().settings().snapping.toggle();
            return true;
        }
        if (UiKit.inside(mx, my, cx + 20, 6, cx + 80, 22)) {
            NexusCore.get().save();
            NexusCore.get().platform().openScreen(new ModMenu());
            return true;
        }
        if (UiKit.inside(mx, my, cx + 84, 6, cx + 124, 22)) {
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
