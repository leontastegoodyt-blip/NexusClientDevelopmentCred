package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.function.Consumer;

/**
 * VersionHooks for Minecraft 1.21.11: the few things that are written differently in each
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
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    /** An effect's name in the player's language, e.g. "Speed". (Since 1.20.5 effects are "holders".) */
    public static String effectName(MobEffectInstance effect) {
        return effect.getEffect().value().getDisplayName().getString();
    }

    /** The id of the biome at a spot, e.g. "dark_forest". */
    public static String biomeId(Level level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
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

    /** The id of the rules channel. (1.21.11 renamed ResourceLocation to Identifier.) */
    static Identifier rulesChannel() {
        return Identifier.fromNamespaceAndPath("nexusclient", "rules");
    }

    /** The Nexus badge font (assets/nexusclient/font/badges.json) – name-tag badges use it. */
    public static net.minecraft.network.chat.Style badgeStyle() {
        return net.minecraft.network.chat.Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath("nexusclient", "badges")));
    }

    /** The dimension's short name: "overworld", "the_nether", "the_end"… (for waypoints). */
    public static String dimensionName(net.minecraft.world.level.Level level) {
        return level.dimension().identifier().getPath();
    }

    /**
     * Recolours one of Minecraft's own textures in place (used for the enchantment glint): every pixel
     * keeps its brightness and see-through-ness but takes the given colour (0xRRGGBB).
     * Returns false if this version hasn't got that texture.
     */
    public static boolean tintTexture(String path, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        Identifier location = Identifier.fromNamespaceAndPath("minecraft", path);
        java.util.Optional<net.minecraft.server.packs.resources.Resource> resource = mc.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return false;
        }
        try (java.io.InputStream in = resource.get().open()) {
            NativeImage image = NativeImage.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getPixel(x, y);
                    paintPixel(image, x, y, com.nexusclient.modern.GlintTint.tint(argb, rgb));
                }
            }
            mc.getTextureManager().register(location, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Nexus " + path, image));
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Puts Minecraft's own texture back (it's loaded again the next time it's drawn). */
    public static void resetTexture(String path) {
        Minecraft.getInstance().getTextureManager().release(Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private static final java.util.Map<String, net.minecraft.core.ClientAsset.Texture> CAPES = new java.util.HashMap<>();

    /**
     * The Nexus cape this player wears, as a texture (made from the downloaded picture the first time
     * it's needed – see mod-core Capes). null = no Nexus cape, or not downloaded yet.
     */
    public static net.minecraft.core.ClientAsset.Texture capeTexture(java.util.UUID player) {
        String id = com.nexusclient.core.online.Capes.capeOf(player);
        if (id == null) {
            return null;
        }
        net.minecraft.core.ClientAsset.Texture done = CAPES.get(id);
        if (done != null) {
            return done;
        }
        byte[] png = com.nexusclient.core.online.Capes.picture(id);
        if (png == null) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            Identifier location = Identifier.fromNamespaceAndPath("nexusclient", "cosmetics/" + id);
            Minecraft.getInstance().getTextureManager().register(location,
                    new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Nexus cape " + id, image));
            net.minecraft.core.ClientAsset.Texture texture = new net.minecraft.core.ClientAsset.ResourceTexture(location, location);
            CAPES.put(id, texture);
            return texture;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** A picture kept on the graphics card for Renderer.picture (see FabricRenderer). */
    private static final class Picture {
        net.minecraft.client.renderer.texture.DynamicTexture texture;
        Identifier location;
        int width;
        int height;
        int version = Integer.MIN_VALUE;
    }

    private static final java.util.Map<String, Picture> PICTURES = new java.util.HashMap<>();

    /** Renderer.picture: sends the pixels to the graphics card only when they change, then draws them. */
    public static void picture(net.minecraft.client.gui.GuiGraphics graphics, String id, int[] pixels, int w, int h,
                               int version, int x1, int y1, int x2, int y2) {
        Picture picture = PICTURES.computeIfAbsent(id, key -> new Picture());
        if (picture.texture == null || picture.width != w || picture.height != h) {
            picture.texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Nexus picture " + id, w, h, false);
            picture.location = Identifier.fromNamespaceAndPath("nexusclient", "picture/" + id);
            Minecraft.getInstance().getTextureManager().register(picture.location, picture.texture);
            picture.width = w;
            picture.height = h;
            picture.version = Integer.MIN_VALUE;
        }
        if (picture.version != version) {
            NativeImage image = picture.texture.getPixels();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = pixels[y * w + x];
                    image.setPixel(x, y, argb);
                }
            }
            picture.texture.upload();
            picture.version = version;
        }
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, picture.location, x1, y1, 0f, 0f, x2 - x1, y2 - y1, w, h, w, h);
    }

    /** Minecraft's main picture (the world, before the HUD is drawn on it): {OpenGL texture, width, height}. */
    public static int[] mainPicture() {
        com.mojang.blaze3d.pipeline.RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return new int[] {((com.mojang.blaze3d.opengl.GlTexture) target.getColorTexture()).glId(), target.width, target.height};
    }
}
