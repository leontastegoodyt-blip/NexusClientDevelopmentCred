package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * FabricNetworking (Minecraft 1.20.1) – the Fabric-only part of the server-safe system: listening on the
 * "nexusclient:rules" channel. Kept apart from VersionHooks so the rest of the code doesn't need Fabric.
 */
public final class FabricNetworking {

    private FabricNetworking() {}

    /**
     * Listens for a server's Nexus rules on the "nexusclient:rules" channel (plain JSON text, see
     * core/server/ServerRules). 1.20.1 uses Fabric's older channel API.
     */
    public static void registerRulesChannel(Consumer<String> onRules) {
        ClientPlayNetworking.registerGlobalReceiver(new ResourceLocation("nexusclient", "rules"),
                (client, handler, buf, sender) -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    client.execute(() -> onRules.accept(json)); // back onto the game thread
                });
    }
}
