package com.nexusclient.modern;

import com.nexusclient.core.online.Badges;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * BadgeText – puts the Nexus badge in front of a player's name tag (see mod-core Badges):
 *
 *     [N] Steve            a Nexus player
 *     [N+] Steve           a Premium player (gold)
 *     [N] Steve [DEV]      the Nexus developer
 *
 * The badges are letters of our own font (assets/nexusclient/font/badges.json), so they're drawn
 * exactly like the rest of the name – same size, same "see through walls" fade, no extra drawing code.
 */
public final class BadgeText {

    private BadgeText() {}

    /** Called by the name-tag mixins: the name to show for this entity. */
    public static Component decorate(Entity entity, Component name) {
        if (name == null || !(entity instanceof Player player)) {
            return name;
        }
        Badges.Badge badge = Badges.of(player.getUUID());
        String clan = Badges.clanTag(player.getUUID());
        if (badge == null && clan == null) {
            return name;
        }
        MutableComponent out = Component.empty();
        if (badge != null) {
            out.append(Component.literal(badge == Badges.Badge.PREMIUM ? Badges.PREMIUM_GLYPH : Badges.NEXUS_GLYPH)
                    .withStyle(VersionHooks.badgeStyle()));
            out.append(" ");
        }
        out.append(name);
        if (badge == Badges.Badge.DEVELOPER) {
            out.append(" ");
            out.append(Component.literal(Badges.DEV_TAG_GLYPH).withStyle(VersionHooks.badgeStyle()));
        }
        if (clan != null) {
            // The clan tag after the name, e.g. "Steve [APPLE]" (Nexus Social).
            out.append(Component.literal(" [" + clan + "]").withStyle(net.minecraft.network.chat.Style.EMPTY
                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0x5FD3FF))));
        }
        return out;
    }

    /** Every tick: lets Badges ask the Nexus server about the players near us (every 20 seconds). */
    public static void tick(Minecraft mc) {
        com.nexusclient.core.online.Capes.tick(() -> nearby(mc));
        Badges.tick(() -> {
            Collection<UUID> ids = new ArrayList<>();
            if (mc.level != null) {
                for (Player player : mc.level.players()) {
                    ids.add(player.getUUID());
                }
            }
            return ids;
        });
    }

    /** The players near us (for asking the Nexus server about badges and capes). */
    private static Collection<UUID> nearby(Minecraft mc) {
        Collection<UUID> ids = new ArrayList<>();
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                ids.add(player.getUUID());
            }
        }
        return ids;
    }

    /** True if this text is the entity's own name (not, say, the score line under it). */
    public static boolean isOwnName(Entity entity, Component text) {
        return text != null && entity != null && text.getString().equals(entity.getDisplayName().getString());
    }
}
