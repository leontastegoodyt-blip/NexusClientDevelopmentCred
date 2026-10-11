package com.nexusclient.fabric;

import com.nexusclient.core.platform.GameInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FabricGameInfo – answers mod-core's questions about the game (FPS, position, items…) by asking
 * Minecraft 26.3 directly.
 */
public class FabricGameInfo implements GameInfo {

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<String, ItemStack> icons = new HashMap<>();

    private LocalPlayer player() {
        return mc.player;
    }

    @Override
    public boolean inWorld() {
        return mc.player != null && mc.level != null;
    }

    @Override
    public int fps() {
        return mc.getFps();
    }

    @Override
    public int ping() {
        if (mc.getConnection() == null || mc.player == null) {
            return 0;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    @Override
    public double x() {
        return inWorld() ? player().getX() : 0;
    }

    @Override
    public double y() {
        return inWorld() ? player().getY() : 0;
    }

    @Override
    public double z() {
        return inWorld() ? player().getZ() : 0;
    }

    @Override
    public float yaw() {
        return inWorld() ? player().getYRot() : 0;
    }

    @Override
    public String biome() {
        if (!inWorld()) {
            return "";
        }
        return mc.level.getBiome(player().blockPosition()).unwrapKey()
                .map(key -> prettify(key.identifier().getPath()))
                .orElse("Unknown");
    }

    @Override
    public String serverAddress() {
        ServerData server = mc.getCurrentServer();
        return server == null ? "Singleplayer" : server.ip;
    }

    @Override
    public long dayTime() {
        return inWorld() ? mc.level.getDefaultClockTime() : 0;
    }

    @Override
    public boolean isKeyDown(String action) {
        return switch (action) {
            case "forward" -> mc.options.keyUp.isDown();
            case "back" -> mc.options.keyDown.isDown();
            case "left" -> mc.options.keyLeft.isDown();
            case "right" -> mc.options.keyRight.isDown();
            case "jump" -> mc.options.keyJump.isDown();
            case "sneak" -> mc.options.keyShift.isDown();
            case "attack" -> mc.options.keyAttack.isDown();
            case "use" -> mc.options.keyUse.isDown();
            default -> false;
        };
    }

    @Override
    public List<ItemInfo> armor() {
        List<ItemInfo> items = new ArrayList<>();
        if (!inWorld()) {
            return items;
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            addIfPresent(items, player().getItemBySlot(slot));
        }
        return items;
    }

    @Override
    public List<ItemInfo> heldItems() {
        List<ItemInfo> items = new ArrayList<>();
        if (inWorld()) {
            addIfPresent(items, player().getMainHandItem());
            addIfPresent(items, player().getOffhandItem());
        }
        return items;
    }

    private static void addIfPresent(List<ItemInfo> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(new ItemInfo(stack, stack.getHoverName().getString(), stack.getCount(), stack.getDamageValue(),
                    stack.getMaxDamage()));
        }
    }

    @Override
    public int countItem(String itemId) {
        if (!inWorld()) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        int total = 0;
        for (int i = 0; i < player().getInventory().getContainerSize(); i++) {
            ItemStack stack = player().getInventory().getItem(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public Object itemIcon(String itemId) {
        return icons.computeIfAbsent(itemId, id -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))));
    }

    @Override
    public List<EffectInfo> effects() {
        List<EffectInfo> effects = new ArrayList<>();
        if (!inWorld()) {
            return effects;
        }
        for (MobEffectInstance effect : player().getActiveEffects()) {
            effects.add(new EffectInfo(effect.getEffect().value().getDisplayName().getString(), effect.getAmplifier(),
                    effect.isInfiniteDuration() ? -1 : effect.getDuration()));
        }
        return effects;
    }

    @Override
    public List<String> resourcePacks() {
        List<String> packs = new ArrayList<>();
        for (String pack : mc.options.resourcePacks) {
            if (pack.startsWith("file/")) {
                packs.add(0, pack.substring(5).replaceAll("\\.zip$", "")); // highest priority first
            }
        }
        return packs;
    }

    @Override
    public String playerName() {
        return mc.getUser().getName();
    }

    @Override
    public int[] windowBounds() {
        var window = mc.getWindow();
        return new int[] {window.getX(), window.getY(), window.getWidth(), window.getHeight()};
    }

    @Override
    public boolean fullscreen() {
        var window = mc.getWindow();
        return window.isExclusiveFullscreen()
                || (window.getWidth() >= window.getScreenWidth() && window.getHeight() >= window.getScreenHeight());
    }

    @Override
    public boolean menuOpen() {
        return mc.gui.screen() != null;
    }

    // ------------------------------------------------------------------ waypoints and minimap

    @Override
    public float pitch() {
        return inWorld() ? player().getXRot() : 0;
    }

    @Override
    public double eyeY() {
        return inWorld() ? player().getEyeY() : 0;
    }

    @Override
    public float fov() {
        // The FOV setting, narrowed while zooming (Zoom divides it by how far it zooms in).
        double zoom = Math.max(1, com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.module.Mods.Zoom.class).current);
        return (float) (mc.options.fov().get() / zoom);
    }

    @Override
    public String worldKey() {
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            return server.ip;
        }
        return mc.getSingleplayerServer() != null
                ? "world:" + mc.getSingleplayerServer().getWorldData().getLevelName() : "world";
    }

    @Override
    public String dimension() {
        return inWorld() ? mc.level.dimension().identifier().getPath() : "overworld";
    }

    @Override
    public float health() {
        return inWorld() ? player().getHealth() : 20;
    }

    @Override
    public int mapColour(int x, int z) {
        if (!inWorld()) {
            return 0;
        }
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, surfaceY(x, z), z);
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return 0; // nothing there, or that part of the world isn't loaded yet
        }
        return state.getMapColor(mc.level, pos).col;
    }

    @Override
    public int surfaceY(int x, int z) {
        if (!inWorld()) {
            return 0;
        }
        // The height map gives the first free space ABOVE the top block, so the block is one lower.
        return mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    /** "dark_forest" → "Dark Forest" */
    private static String prettify(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return out.toString().trim();
    }

    @Override
    public int[] lookedAtBlock() {
        if (!inWorld()) {
            return null;
        }
        // Up to 128 blocks away (further than you can reach), for party pings.
        net.minecraft.world.phys.HitResult hit = mc.player.pick(128, 1.0f, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.core.BlockPos pos = block.getBlockPos();
            return new int[] {pos.getX(), pos.getY(), pos.getZ()};
        }
        return null;
    }
}
