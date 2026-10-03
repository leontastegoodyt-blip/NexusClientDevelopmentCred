package com.nexusclient.backend.cosmetics;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CosmeticsController – Nexus cosmetics (capes, wings, hats, emotes). Only other Nexus players see
 * them: the in-game mod asks this server what each nearby player wears.
 *   GET  /api/cosmetics                     the catalogue + what you own and wear (signed in)
 *   POST /api/cosmetics/equip {type, id}    wear an item (id empty = take it off)
 *   GET  /api/cosmetics/worn?uuids=a,b      public: what these players wear {uuid: {CAPE: id, ...}}
 * Pictures: /cosmetics/<id>.png. Everything is free for now (pricing undecided), so you own all free items.
 *
 * Uses plain SQL (JdbcClient) – a small, simple way to talk to the database without entity classes.
 */
@RestController
public class CosmeticsController {

    private static final Set<String> TYPES = Set.of("CAPE", "WINGS", "HAT", "EMOTE");

    public record Item(String id, String type, String name, Integer priceCents, boolean owned, boolean equipped,
                       String image) {}

    public record EquipRequest(@NotBlank String type, String id) {}

    private final JdbcClient db;

    public CosmeticsController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping("/api/cosmetics")
    public List<Item> catalogue(@RequestAttribute(SignedInCheck.ME) UUID me) {
        Set<String> equipped = Set.copyOf(db.sql("select cosmetic_id from cosmetic_equipped where player_uuid = ?")
                .param(me).query(String.class).list());
        return db.sql("select id, type, name, price_cents from cosmetics order by type, name")
                .query((rs, row) -> {
                    Integer price = (Integer) rs.getObject("price_cents");
                    String id = rs.getString("id");
                    return new Item(id, rs.getString("type"), rs.getString("name"), price, price == null,
                            equipped.contains(id), "/cosmetics/" + id + ".png");
                }).list();
    }

    @PostMapping("/api/cosmetics/equip")
    @Transactional
    public Map<String, Boolean> equip(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody EquipRequest body) {
        String type = body.type().toUpperCase();
        if (!TYPES.contains(type)) {
            throw NexusException.badRequest("Unknown cosmetic type");
        }
        db.sql("delete from cosmetic_equipped where player_uuid = ? and type = ?").param(me).param(type).update();
        if (body.id() != null && !body.id().isBlank()) {
            List<Map<String, Object>> found = db.sql("select type, price_cents from cosmetics where id = ?")
                    .param(body.id()).query().listOfRows();
            if (found.isEmpty() || !type.equals(found.get(0).get("type"))) {
                throw NexusException.notFound("No such " + type.toLowerCase());
            }
            if (found.get(0).get("price_cents") != null) {
                throw NexusException.forbidden("You don't own that item"); // buying isn't built yet
            }
            db.sql("insert into cosmetic_equipped (player_uuid, type, cosmetic_id) values (?, ?, ?)")
                    .param(me).param(type).param(body.id()).update();
        }
        return Map.of("ok", true);
    }

    /** Public – no sign-in needed (see SignedInCheck), so every Nexus player's game can show them. */
    @GetMapping("/api/cosmetics/worn")
    public Map<String, Map<String, String>> worn(@RequestParam String uuids) {
        List<UUID> list = Arrays.stream(uuids.split(",")).limit(200).map(String::trim).filter(s -> !s.isEmpty())
                .map(UUID::fromString).toList();
        Map<String, Map<String, String>> result = new HashMap<>();
        if (list.isEmpty()) {
            return result;
        }
        db.sql("select player_uuid, type, cosmetic_id from cosmetic_equipped where player_uuid in (:ids)")
                .param("ids", list)
                .query((rs, row) -> {
                    result.computeIfAbsent(rs.getObject("player_uuid").toString(), k -> new HashMap<>())
                            .put(rs.getString("type"), rs.getString("cosmetic_id"));
                    return null;
                }).list();
        return result;
    }
}
