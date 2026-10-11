package com.nexusclient.fabric;

import com.nexusclient.core.online.Badges;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
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

    private static final Style BADGE_FONT = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("nexusclient", "badges")));

    private BadgeText() {}

    /** Called by BadgeMixin: the name to show for this entity. */
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
                    .withStyle(BADGE_FONT));
            out.append(" ");
        }
        out.append(name);
        if (badge == Badges.Badge.DEVELOPER) {
            out.append(" ");
            out.append(Component.literal(Badges.DEV_TAG_GLYPH).withStyle(BADGE_FONT));
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
}
