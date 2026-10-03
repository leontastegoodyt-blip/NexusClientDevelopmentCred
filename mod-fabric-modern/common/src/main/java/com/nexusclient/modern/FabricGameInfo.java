package com.nexusclient.modern;

import com.nexusclient.core.platform.GameInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
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
 * Minecraft 1.20–1.21 directly. Item, effect and biome look-ups differ per version (VersionHooks).
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
        return inWorld() ? prettify(VersionHooks.biomeId(mc.level, player().blockPosition())) : "";
    }

    @Override
    public String serverAddress() {
        ServerData server = mc.getCurrentServer();
        return server == null ? "Singleplayer" : server.ip;
    }

    @Override
    public long dayTime() {
        return inWorld() ? mc.level.getDayTime() % 24000L : 0;
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
        Item item = VersionHooks.item(itemId);
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
        return icons.computeIfAbsent(itemId, id -> new ItemStack(VersionHooks.item(id)));
    }

    @Override
    public List<EffectInfo> effects() {
        List<EffectInfo> effects = new ArrayList<>();
        if (!inWorld()) {
            return effects;
        }
        for (MobEffectInstance effect : player().getActiveEffects()) {
            effects.add(new EffectInfo(VersionHooks.effectName(effect), effect.getAmplifier(),
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
        return window.isFullscreen()
                || (window.getWidth() >= window.getScreenWidth() && window.getHeight() >= window.getScreenHeight());
    }

    @Override
    public boolean menuOpen() {
        return mc.screen != null;
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
}
