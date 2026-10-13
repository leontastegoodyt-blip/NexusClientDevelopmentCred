package com.nexusclient.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

/**
 * NexusFabric – where the Nexus mod starts inside Minecraft 26.3 on Fabric (Fabric calls
 * onInitializeClient when the game opens). It only plugs NexusGame (shared with the Forge version)
 * into Fabric's events: keys, HUD drawing, the crosshair, attacks, joining/leaving servers, the
 * server-rules channel and the game tick.
 */
public class NexusFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NexusGame.start(FabricLoader.getInstance().getGameDir().toFile());

        for (KeyMapping key : NexusGame.keys()) {
            KeyMappingHelper.registerKeyMapping(key);
        }

        // HUD: every switched-on HUD mod, on top of the normal game HUD.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nexusclient", "hud"),
                (graphics, delta) -> NexusGame.drawHud(graphics));

        // Custom crosshair: replaces Minecraft's own one while the Crosshair mod is on.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
            if (NexusGame.customCrosshair()) {
                NexusGame.drawCrosshair(graphics);
            } else {
                original.extractRenderState(graphics, delta);
            }
        });

        // Combo counter + PvP stats: count our hits.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                NexusGame.attacked(player, entity);
            }
            return InteractionResult.PASS;
        });

        // Server-safe system: which server we're on, and the server's own rules message.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> NexusGame.joinedServer());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NexusGame.leftServer());
        PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (payload, context) -> NexusGame.rulesMessage(payload.json()));

        ClientTickEvents.END_CLIENT_TICK.register(NexusGame::tick);
    }
}
