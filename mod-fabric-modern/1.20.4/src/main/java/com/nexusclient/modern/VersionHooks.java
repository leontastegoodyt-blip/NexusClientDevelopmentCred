package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * VersionHooks for Minecraft 1.20.2 – 1.20.4: the few things that are written differently in each
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

    /**
     * Self-test only: makes a new world and joins it. This build also runs on 1.20.2, whose
     * createFreshLevel doesn't have the last "parent screen" input yet – so if the 1.20.3+ form is
     * missing, find the older one by its inputs and call that instead.
     */
    public static void createTestWorld(Minecraft mc) {
        WorldOpenFlows flows = mc.createWorldOpenFlows();
        String name = "Nexus self-test " + System.currentTimeMillis();
        Function<RegistryAccess, WorldDimensions> dimensions = WorldPresets::createNormalWorldDimensions;
        try {
            flows.createFreshLevel(name, MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, dimensions,
                    new TitleScreen());
        } catch (NoSuchMethodError onlyOn1202) {
            for (Method method : WorldOpenFlows.class.getMethods()) {
                Class<?>[] inputs = method.getParameterTypes();
                if (inputs.length == 4 && inputs[0] == String.class && inputs[1] == LevelSettings.class
                        && inputs[2] == WorldOptions.class && inputs[3] == Function.class) {
                    try {
                        method.invoke(flows, name, MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, dimensions);
                        return;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Couldn't make the self-test world", e);
                    }
                }
            }
            throw onlyOn1202;
        }
    }

    /**
     * Listens for a server's Nexus rules on the "nexusclient:rules" channel (plain JSON text, see
     * core/server/ServerRules). 1.20.2–1.20.4 still use Fabric's older channel API.
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
