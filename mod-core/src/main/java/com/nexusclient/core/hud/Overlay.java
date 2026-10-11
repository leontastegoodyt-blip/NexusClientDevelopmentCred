package com.nexusclient.core.hud;

import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;

/**
 * Overlay – a mod that draws over the WHOLE screen while playing (not one movable HUD box), e.g.
 * waypoint markers floating where the waypoints are. NexusCore draws every active Overlay right
 * after the HUD modules.
 */
public interface Overlay {
    void drawOverlay(Renderer renderer, GameInfo game);
}
