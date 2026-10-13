package com.nexusclient.modern;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionResult;

/**
 * NexusModern – where the Nexus mod starts inside Minecraft 1.20–1.21.11 on Fabric (Fabric calls
 * onInitializeClient when the game opens). It only plugs ModernGame (shared with the Forge build)
 * into Fabric's events: keys, HUD drawing, attacks, joining/leaving servers, the server-rules
 * channel and the game tick. The Fabric-only bits that differ per version are in FabricHooks
 * (the HUD) and FabricNetworking (the rules channel).
 */
public class NexusModern implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModernGame.start(FabricLoader.getInstance().getGameDir().toFile());

        for (KeyMapping key : ModernGame.keys()) {
            KeyBindingHelper.registerKeyBinding(key);
        }

        // HUD: the custom crosshair and every switched-on HUD mod, on top of the normal game HUD.
        FabricHooks.registerHud(ModernGame::drawHud);

        // Combo counter + PvP stats: count our hits.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                ModernGame.attacked(player, entity);
            }
            return InteractionResult.PASS;
        });

        // Server-safe system: which server we're on, and the server's own rules message.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ModernGame.joinedServer());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ModernGame.leftServer());
        FabricNetworking.registerRulesChannel(ModernGame::rulesMessage);

        ClientTickEvents.END_CLIENT_TICK.register(ModernGame::tick);
    }
}
