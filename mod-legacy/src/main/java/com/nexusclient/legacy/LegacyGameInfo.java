package com.nexusclient.legacy;

import com.nexusclient.core.platform.GameInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LegacyGameInfo – answers mod-core's questions about the game (FPS, position, items…) by asking
 * Minecraft 1.8.9 directly. 1.8.9 has no off-hand, so heldItems() is just the main hand.
 */
public class LegacyGameInfo implements GameInfo {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<String, ItemStack> icons = new HashMap<String, ItemStack>();

    private ClientPlayerEntity player() {
        return mc.player;
    }

    @Override
    public boolean inWorld() {
        return mc.player != null && mc.world != null;
    }

    @Override
    public int fps() {
        return MinecraftClient.getCurrentFps();
    }

    @Override
    public int ping() {
        if (mc.getNetworkHandler() == null || mc.player == null) {
            return 0;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    @Override
    public double x() {
        return inWorld() ? player().x : 0;
    }

    @Override
    public double y() {
        return inWorld() ? player().y : 0;
    }

    @Override
    public double z() {
        return inWorld() ? player().z : 0;
    }

    @Override
    public float yaw() {
        return inWorld() ? player().yaw : 0;
    }

    @Override
    public String biome() {
        return inWorld() ? mc.world.getBiome(new BlockPos(player())).name : "";
    }

    @Override
    public String serverAddress() {
        ServerInfo server = mc.getCurrentServerEntry();
        return server == null ? "Singleplayer" : server.address;
    }

    @Override
    public long dayTime() {
        return inWorld() ? mc.world.getTimeOfDay() % 24000L : 0;
    }

    @Override
    public boolean isKeyDown(String action) {
        if ("forward".equals(action)) return mc.options.forwardKey.isPressed();
        if ("back".equals(action)) return mc.options.backKey.isPressed();
        if ("left".equals(action)) return mc.options.leftKey.isPressed();
        if ("right".equals(action)) return mc.options.rightKey.isPressed();
        if ("jump".equals(action)) return mc.options.jumpKey.isPressed();
        if ("sneak".equals(action)) return mc.options.sneakKey.isPressed();
        if ("attack".equals(action)) return mc.options.attackKey.isPressed();
        if ("use".equals(action)) return mc.options.useKey.isPressed();
        return false;
    }

    @Override
    public List<ItemInfo> armor() {
        List<ItemInfo> items = new ArrayList<ItemInfo>();
        if (inWorld()) {
            for (int slot = 3; slot >= 0; slot--) { // 3 = helmet … 0 = boots
                addIfPresent(items, player().inventory.armor[slot]);
            }
        }
        return items;
    }

    @Override
    public List<ItemInfo> heldItems() {
        List<ItemInfo> items = new ArrayList<ItemInfo>();
        if (inWorld()) {
            addIfPresent(items, player().inventory.getMainHandStack());
        }
        return items;
    }

    private static void addIfPresent(List<ItemInfo> items, ItemStack stack) {
        if (stack != null && stack.getItem() != null) {
            items.add(new ItemInfo(stack, stack.getCustomName(), stack.count, stack.getDamage(), stack.getMaxDamage()));
        }
    }

    @Override
    public int countItem(String itemId) {
        if (!inWorld()) {
            return 0;
        }
        Item item = Item.REGISTRY.get(new Identifier(itemId));
        int total = 0;
        for (ItemStack stack : player().inventory.main) {
            if (stack != null && stack.getItem() == item) {
                total += stack.count;
            }
        }
        return total;
    }

    @Override
    public Object itemIcon(String itemId) {
        ItemStack icon = icons.get(itemId);
        if (icon == null) {
            Item item = Item.REGISTRY.get(new Identifier(itemId));
            if (item == null) {
                return null;
            }
            icon = new ItemStack(item);
            icons.put(itemId, icon);
        }
        return icon;
    }

    @Override
    public List<EffectInfo> effects() {
        List<EffectInfo> effects = new ArrayList<EffectInfo>();
        if (inWorld()) {
            for (StatusEffectInstance effect : player().getStatusEffectInstances()) {
                effects.add(new EffectInfo(I18n.translate(effect.getTranslationKey()), effect.getAmplifier(),
                        effect.getDuration()));
            }
        }
        return effects;
    }

    @Override
    public List<String> resourcePacks() {
        List<String> packs = new ArrayList<String>();
        for (String pack : mc.options.resourcePacks) { // 1.8.9 already lists the top pack first
            packs.add(pack.replaceAll("\\.zip$", ""));
        }
        return packs;
    }

    @Override
    public String playerName() {
        return mc.getSession().getUsername();
    }

    @Override
    public int[] windowBounds() {
        return new int[] {Display.getX(), Display.getY(), mc.width, mc.height};
    }

    @Override
    public boolean fullscreen() {
        return mc.isFullscreen();
    }

    @Override
    public boolean menuOpen() {
        return mc.currentScreen != null;
    }

    // ------------------------------------------------------------------ waypoints and minimap

    @Override
    public float pitch() {
        return mc.player != null ? mc.player.pitch : 0;
    }

    @Override
    public double eyeY() {
        return mc.player != null ? mc.player.y + mc.player.getEyeHeight() : 0;
    }

    @Override
    public float fov() {
        // The FOV setting, narrowed while zooming.
        double zoom = Math.max(1, com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.module.Mods.Zoom.class).current);
        return (float) (mc.options.fov / zoom);
    }

    @Override
    public String worldKey() {
        ServerInfo server = mc.getCurrentServerEntry();
        if (server != null) {
            return server.address;
        }
        return mc.getServer() != null ? "world:" + mc.getServer().getLevelName() : "world";
    }

    @Override
    public String dimension() {
        if (mc.player == null) {
            return "overworld";
        }
        // 1.8.9 numbers its dimensions: 0 = Overworld, -1 = Nether, 1 = End.
        int dimension = mc.player.dimension;
        return dimension == -1 ? "the_nether" : dimension == 1 ? "the_end" : dimension == 0 ? "overworld" : "dim" + dimension;
    }

    @Override
    public float health() {
        return mc.player != null ? mc.player.getHealth() : 20;
    }

    @Override
    public int mapColour(int x, int z) {
        if (mc.world == null) {
            return 0;
        }
        BlockPos pos = new BlockPos(x, surfaceY(x, z), z);
        net.minecraft.block.BlockState state = mc.world.getBlockState(pos);
        net.minecraft.block.Block block = state.getBlock();
        if (block.getMaterial() == net.minecraft.block.material.Material.AIR) {
            return 0; // nothing there, or that part of the world isn't loaded yet
        }
        return block.getMaterialColor(state).color;
    }

    @Override
    public int surfaceY(int x, int z) {
        if (mc.world == null) {
            return 0;
        }
        // getTopPosition gives the free space ABOVE the top block, so the block is one lower.
        return mc.world.getTopPosition(new BlockPos(x, 0, z)).getY() - 1;
    }

    @Override
    public int[] lookedAtBlock() {
        if (mc.player == null) {
            return null;
        }
        // Up to 128 blocks away (further than you can reach), for party pings.
        net.minecraft.util.hit.BlockHitResult hit = mc.player.rayTrace(128, 1.0f);
        if (hit != null && hit.type == net.minecraft.util.hit.BlockHitResult.Type.BLOCK && hit.getBlockPos() != null) {
            return new int[] {hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()};
        }
        return null;
    }

    @Override
    public List<String> sidebar() {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
        net.minecraft.scoreboard.ScoreboardObjective objective = null;
        net.minecraft.scoreboard.Team team = board.getPlayerTeam(mc.player.getGameProfile().getName());
        if (team != null && team.getFormatting() != null && team.getFormatting().getColorIndex() >= 0) {
            objective = board.getObjectiveForSlot(3 + team.getFormatting().getColorIndex()); // a team's own sidebar
        }
        if (objective == null) {
            objective = board.getObjectiveForSlot(1); // 1 = the sidebar
        }
        if (objective == null) {
            return null;
        }
        List<String> lines = new java.util.ArrayList<String>();
        lines.add(objective.getDisplayName());
        List<net.minecraft.scoreboard.ScoreboardPlayerScore> scores =
                new java.util.ArrayList<net.minecraft.scoreboard.ScoreboardPlayerScore>(board.getAllPlayerScores(objective));
        for (int i = scores.size() - 1; i >= 0; i--) {
            String name = scores.get(i).getPlayerName();
            if (name == null || name.startsWith("#")) {
                scores.remove(i);
            }
        }
        java.util.Collections.sort(scores, new java.util.Comparator<net.minecraft.scoreboard.ScoreboardPlayerScore>() {
            public int compare(net.minecraft.scoreboard.ScoreboardPlayerScore a, net.minecraft.scoreboard.ScoreboardPlayerScore b) {
                return a.getScore() != b.getScore() ? (b.getScore() < a.getScore() ? -1 : 1)
                        : a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
            }
        });
        for (net.minecraft.scoreboard.ScoreboardPlayerScore score : scores.subList(0, Math.min(15, scores.size()))) {
            String name = score.getPlayerName();
            lines.add(net.minecraft.scoreboard.Team.decorateName(board.getPlayerTeam(name), name) + "\t" + score.getScore());
        }
        return lines;
    }

    @Override
    public List<String> bossBars() {
        List<String> bars = new java.util.ArrayList<String>();
        // 1.8.9 has just one boss bar, kept in static fields; framesToLive counts down while it's shown.
        if (net.minecraft.entity.boss.BossBar.name != null && net.minecraft.entity.boss.BossBar.framesToLive > 0) {
            bars.add(net.minecraft.entity.boss.BossBar.name + "\t" + net.minecraft.entity.boss.BossBar.percent + "\tPINK");
        }
        return bars;
    }
}
