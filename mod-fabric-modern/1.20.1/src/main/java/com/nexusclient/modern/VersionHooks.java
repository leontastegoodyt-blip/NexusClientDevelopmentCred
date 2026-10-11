package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.platform.NativeImage;
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

    /** The red tint Minecraft gives hurt mobs (as ARGB colour) – see HitColour. */
    public static final int DEFAULT_HURT_COLOUR = 0xB2FF0000;

    /** Sets one pixel of a texture. 1.20.x stores pixels with red and blue swapped (ABGR). */
    public static void paintPixel(NativeImage image, int x, int y, int argb) {
        int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        image.setPixelRGBA(x, y, abgr);
    }

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

    /** The Nexus badge font (assets/nexusclient/font/badges.json) – name-tag badges use it. */
    public static net.minecraft.network.chat.Style badgeStyle() {
        return net.minecraft.network.chat.Style.EMPTY.withFont(new ResourceLocation("nexusclient", "badges"));
    }

    /** The dimension's short name: "overworld", "the_nether", "the_end"… (for waypoints). */
    public static String dimensionName(net.minecraft.world.level.Level level) {
        return level.dimension().location().getPath();
    }
}
