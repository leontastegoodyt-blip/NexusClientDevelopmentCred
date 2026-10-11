package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.platform.NativeImage;
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

    /** The Nexus badge font (assets/nexusclient/font/badges.json) – name-tag badges use it. */
    public static net.minecraft.network.chat.Style badgeStyle() {
        return net.minecraft.network.chat.Style.EMPTY.withFont(new ResourceLocation("nexusclient", "badges"));
    }

    /** The dimension's short name: "overworld", "the_nether", "the_end"… (for waypoints). */
    public static String dimensionName(net.minecraft.world.level.Level level) {
        return level.dimension().location().getPath();
    }

    /**
     * Recolours one of Minecraft's own textures in place (used for the enchantment glint): every pixel
     * keeps its brightness and see-through-ness but takes the given colour (0xRRGGBB).
     * Returns false if this version hasn't got that texture.
     */
    public static boolean tintTexture(String path, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation location = new ResourceLocation("minecraft", path);
        java.util.Optional<net.minecraft.server.packs.resources.Resource> resource = mc.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return false;
        }
        try (java.io.InputStream in = resource.get().open()) {
            NativeImage image = NativeImage.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y); int argb = (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
                    paintPixel(image, x, y, com.nexusclient.modern.GlintTint.tint(argb, rgb));
                }
            }
            mc.getTextureManager().register(location, new net.minecraft.client.renderer.texture.DynamicTexture(image));
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Puts Minecraft's own texture back (it's loaded again the next time it's drawn). */
    public static void resetTexture(String path) {
        Minecraft.getInstance().getTextureManager().release(new ResourceLocation("minecraft", path));
    }

    /** Motion Blur works on this version (a Nexus post-effect shader, see MotionBlurControl). */
    public static final boolean MOTION_BLUR_SUPPORTED = true;

    /** Switches the Motion Blur effect on at a strength (10–90), or off (-1). */
    public static void setMotionBlur(int strength) {
        Minecraft mc = Minecraft.getInstance();
        if (strength < 0) {
            mc.gameRenderer.shutdownEffect();
        } else {
            ((com.nexusclient.modern.mixin.GameRendererInvoker) mc.gameRenderer)
                    .nexus$loadEffect(new ResourceLocation("shaders/post/nexus_motion_blur_" + strength + ".json"));
        }
    }

    private static final java.util.Map<String, ResourceLocation> CAPES = new java.util.HashMap<>();

    /**
     * The Nexus cape this player wears, as a texture (made from the downloaded picture the first time
     * it's needed – see mod-core Capes). null = no Nexus cape, or not downloaded yet.
     */
    public static ResourceLocation capeTexture(java.util.UUID player) {
        String id = com.nexusclient.core.online.Capes.capeOf(player);
        if (id == null) {
            return null;
        }
        ResourceLocation done = CAPES.get(id);
        if (done != null) {
            return done;
        }
        byte[] png = com.nexusclient.core.online.Capes.picture(id);
        if (png == null) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            ResourceLocation location = new ResourceLocation("nexusclient", "cosmetics/" + id);
            Minecraft.getInstance().getTextureManager().register(location,
                    new net.minecraft.client.renderer.texture.DynamicTexture(image));
            CAPES.put(id, location);
            return location;
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
