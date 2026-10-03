package com.nexusclient.launcher.torcherrig;

/**
 * GameSettingsPlan – the in-game video settings TorcherRig picks for a PC.
 *
 * @param renderDistance     how far you can see, in chunks (1 chunk = 16 blocks)
 * @param simulationDistance how far away the world keeps "living" (mobs, crops…), in chunks – 1.18+ only
 * @param graphics           "FAST", "FANCY" or "FABULOUS"
 * @param particles          "ALL", "DECREASED" or "MINIMAL"
 * @param clouds             "OFF", "FAST" or "FANCY"
 * @param entityShadows      the round shadows under mobs and players
 * @param smoothLighting     softer, nicer light (costs a little speed)
 * @param maxFps             frames-per-second limit (260 = unlimited in Minecraft)
 */
public record GameSettingsPlan(int renderDistance, int simulationDistance, String graphics, String particles,
                               String clouds, boolean entityShadows, boolean smoothLighting, int maxFps) {
}
