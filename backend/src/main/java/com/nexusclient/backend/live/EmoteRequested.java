package com.nexusclient.backend.live;

import java.util.UUID;

/** EmoteRequested – a player pressed their emote key in the game (EmoteRelay decides what happens). */
public record EmoteRequested(UUID player) {}
