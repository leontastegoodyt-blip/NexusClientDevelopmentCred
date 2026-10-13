package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.function.Consumer;

/**
 * FabricNetworking (Minecraft 1.20.6) – the Fabric-only part of the server-safe system: listening on the
 * "nexusclient:rules" channel. Kept apart from VersionHooks so the rest of the code doesn't need Fabric.
 */
public final class FabricNetworking {

    private FabricNetworking() {}

    /**
     * Listens for a server's Nexus rules on the "nexusclient:rules" channel (plain JSON text, see
     * core/server/ServerRules). Since 1.20.5 plugin messages are "payloads" (see RulesPayload).
     */
    public static void registerRulesChannel(Consumer<String> onRules) {
        PayloadTypeRegistry.playS2C().register(RulesPayload.TYPE, RulesPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (payload, context) -> onRules.accept(payload.json()));
    }
}
