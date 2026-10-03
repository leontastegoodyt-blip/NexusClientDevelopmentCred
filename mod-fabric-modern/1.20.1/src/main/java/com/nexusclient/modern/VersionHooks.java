package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * VersionHooks for Minecraft 1.20 – 1.20.1: the few things that are written differently in each
 * Minecraft version. Every version folder has its own copy of this class.
 */
public final class VersionHooks {

    private VersionHooks() {}

    /** Minecraft's "does the player have this effect?" method, exactly (for the Fullbright hook, LightTextureMixin). */
    public static final String HAS_EFFECT = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z";

    /** An item type by its id, e.g. "minecraft:arrow". */
    public static Item item(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    /** An effect's name in the player's language, e.g. "Speed". */
    public static String effectName(MobEffectInstance effect) {
        return effect.getEffect().getDisplayName().getString();
    }

    /** The id of the biome at a spot, e.g. "dark_forest". */
    public static String biomeId(Level level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("unknown");
    }

    /** Self-test only: makes a new world and joins it. */
    public static void createTestWorld(Minecraft mc) {
        mc.createWorldOpenFlows().createFreshLevel("Nexus self-test " + System.currentTimeMillis(),
                MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions);
    }

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
