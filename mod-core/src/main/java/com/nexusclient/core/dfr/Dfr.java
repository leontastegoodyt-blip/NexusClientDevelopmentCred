package com.nexusclient.core.dfr;

import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;
import com.nexusclient.core.settings.NumberSetting;

/**
 * Dfr – DFR (Dual Frame Reset), Nexus's signature feature (the project brief section 5): an extra generated
 * frame is shown between each pair of real frames, so the game looks smoother.
 *
 * Modes (the player picks):
 *   PvP – "Smooth": the extra frame is a blend of the last two real frames (smoothest, but each real
 *                   frame is shown a little later – slight input lag)
 *         "Low lag": the extra frame is the newest real frame moved by the mouse movement that has
 *                   already happened (camera prediction / reprojection) – almost no added lag
 *   SMP      – Smooth, a bit gentler (80% strength)
 *   Creative – Smooth at full strength (building and flying look calm)
 *
 * The drawing itself happens in the loader module (e.g. mod-fabric's DfrRenderer). This class holds
 * the settings and the live numbers (how many frames were generated) for the FPS HUD.
 */
public class Dfr extends Module {

    public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Mode", "SMP", "PvP", "SMP", "Creative"));
    public final ChoiceSetting pvpStyle = add(new ChoiceSetting("pvpStyle", "PvP style", "Low lag", "Smooth", "Low lag"));
    public final NumberSetting strength = add(new NumberSetting("strength", "Strength", 100, 10, 100, 5, "%"));
    public final BooleanSetting showInFps = add(new BooleanSetting("showInFps", "FPS HUD shows real + generated FPS", true));

    /** Generated frames in the last second (updated by the loader module). */
    private volatile int generatedFps;
    private int countThisSecond;
    private long secondStart = System.currentTimeMillis();

    public Dfr() {
        super("dfr", "DFR (Dual Frame Reset)",
                "Adds generated frames when your FPS is low. Rests when the game is already smooth.", Category.VISUAL, false, false);
    }

    /** True if the extra frame should be a camera prediction (Low lag) rather than a blend (Smooth). */
    public boolean lowLag() {
        return mode.get().equals("PvP") && pvpStyle.get().equals("Low lag");
    }

    /** 0–1: how strong the effect is, including the mode's own tuning. */
    public float effectiveStrength() {
        float s = (float) (strength.get() / 100.0);
        return mode.get().equals("SMP") ? s * 0.8f : s;
    }

    /** The loader module calls this for each generated frame it shows. */
    public synchronized void frameGenerated() {
        countThisSecond++;
        tickSecond();
    }

    public synchronized int generatedFps() {
        tickSecond();
        return generatedFps;
    }

    private void tickSecond() {
        long now = System.currentTimeMillis();
        if (now - secondStart >= 1000) {
            generatedFps = countThisSecond;
            countThisSecond = 0;
            secondStart = now;
        }
    }
}
