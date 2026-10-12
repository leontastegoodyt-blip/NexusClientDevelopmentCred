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
 * VersionHooks for Minecraft 1.20.5 – 1.20.6: the few things that are written differently in each
 * Minecraft version. Every version folder has its own copy of this class.
 */
public final class VersionHooks {

    private VersionHooks() {}

    /** Minecraft's "does the player have this effect?" method, exactly (for the Fullbright hook, LightTextureMixin).
     *  Since 1.20.5 effects are passed as "holders". */
    public static final String HAS_EFFECT = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z";

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
        return new ResourceLocation("nexusclient", "rules");
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

    /** A picture kept on the graphics card for Renderer.picture (see FabricRenderer). */
    private static final class Picture {
        net.minecraft.client.renderer.texture.DynamicTexture texture;
        ResourceLocation location;
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
            picture.texture = new net.minecraft.client.renderer.texture.DynamicTexture(w, h, false);
            picture.location = new ResourceLocation("nexusclient", "picture/" + id);
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
                    image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16); // NativeImage stores ABGR
                }
            }
            picture.texture.upload();
            picture.version = version;
        }
        graphics.blit(picture.location, x1, y1, x2 - x1, y2 - y1, 0f, 0f, w, h, w, h);
    }

    /** Minecraft's main picture (the world, before the HUD is drawn on it): {OpenGL texture, width, height}. */
    public static int[] mainPicture() {
        com.mojang.blaze3d.pipeline.RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return new int[] {target.getColorTextureId(), target.width, target.height};
    }

    /** The sidebar scoreboard for Custom Scoreboard: title, then "name\tscore" lines (1.20.3+ names). */
    public static java.util.List<String> sidebar(Minecraft mc) {
        net.minecraft.world.scores.Scoreboard board = mc.level.getScoreboard();
        net.minecraft.world.scores.Objective objective = null;
        net.minecraft.world.scores.PlayerTeam team = board.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            net.minecraft.world.scores.DisplaySlot slot = net.minecraft.world.scores.DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) {
                objective = board.getDisplayObjective(slot); // a team's own sidebar
            }
        }
        if (objective == null) {
            objective = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return null;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(TextCodes.legacy(objective.getDisplayName()));
        java.util.List<net.minecraft.world.scores.PlayerScoreEntry> entries = new java.util.ArrayList<>(board.listPlayerScores(objective));
        entries.removeIf(net.minecraft.world.scores.PlayerScoreEntry::isHidden);
        entries.sort((a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value()) : a.owner().compareToIgnoreCase(b.owner()));
        net.minecraft.network.chat.numbers.NumberFormat format =
                objective.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
        for (net.minecraft.world.scores.PlayerScoreEntry entry : entries.subList(0, Math.min(15, entries.size()))) {
            net.minecraft.world.scores.PlayerTeam owner = board.getPlayersTeam(entry.owner());
            lines.add(TextCodes.legacy(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(owner, entry.ownerName()))
                    + "\t" + TextCodes.legacy(entry.formatValue(format)));
        }
        return lines;
    }
}
