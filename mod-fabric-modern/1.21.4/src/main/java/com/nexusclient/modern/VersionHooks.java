package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.function.Consumer;

/**
 * VersionHooks for Minecraft 1.21.4: the few things that are written differently in each
 * Minecraft version. Every version folder has its own copy of this class.
 */
public final class VersionHooks {

    private VersionHooks() {}

    /** Minecraft's "does the player have this effect?" method, exactly (for the Fullbright hook, LightTextureMixin).
     *  Since 1.20.5 effects are passed as "holders". */
    public static final String HAS_EFFECT = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z";

    /** The red tint Minecraft gives hurt mobs (as ARGB colour) – see HitColour. */
    public static final int DEFAULT_HURT_COLOUR = 0xB3FF0000;

    /** Sets one pixel of a texture (1.21+ uses normal ARGB colours). */
    public static void paintPixel(NativeImage image, int x, int y, int argb) {
        image.setPixel(x, y, argb);
    }

    /** An item type by its id, e.g. "minecraft:arrow". */
    public static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id));
    }

    /** An effect's name in the player's language, e.g. "Speed". (Since 1.20.5 effects are "holders".) */
    public static String effectName(MobEffectInstance effect) {
        return effect.getEffect().value().getDisplayName().getString();
    }

    /** The id of the biome at a spot, e.g. "dark_forest". */
    public static String biomeId(Level level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("unknown");
    }

    /** Self-test only: makes a new world and joins it. */
    public static void createTestWorld(Minecraft mc) {
        mc.createWorldOpenFlows().createFreshLevel("Nexus self-test " + System.currentTimeMillis(),
                MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions,
                new TitleScreen());
    }

    /**
     * Listens for a server's Nexus rules on the "nexusclient:rules" channel (plain JSON text, see
     * core/server/ServerRules). Since 1.20.5 plugin messages are "payloads" (see RulesPayload).
     */
    public static void registerRulesChannel(Consumer<String> onRules) {
        PayloadTypeRegistry.playS2C().register(RulesPayload.TYPE, RulesPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (payload, context) -> onRules.accept(payload.json()));
    }

    /** The id of the rules channel. */
    static ResourceLocation rulesChannel() {
        return ResourceLocation.fromNamespaceAndPath("nexusclient", "rules");
    }
}
