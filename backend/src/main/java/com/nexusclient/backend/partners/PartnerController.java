package com.nexusclient.backend.partners;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PartnerController – Nexus Partner Servers and Premium admin tools.
 *   GET  /api/partners                         public: the partner list (name, address, description,
 *                                               blocked Nexus mods) – the launcher and mod read this
 *   PUT  /api/partners/{id}/rules {blocked}    the partner's own manager (or an admin) sets which
 *                                               Nexus mods are switched off on their server
 *   POST /api/admin/partners {name, address, description, manager}     admins: add a partner
 *   DELETE /api/admin/partners/{id}                                    admins: remove a partner
 *   POST /api/admin/players/{uuid}/premium {days}                      admins: give Premium (0 = remove)
 * Payments for Premium aren't built yet (price and provider are still to be decided by the owner).
 */
@RestController
public class PartnerController {

    public record PartnerView(long id, String name, String address, String description, List<String> blockedMods,
                              boolean youManage) {}

    public record RulesRequest(@NotNull List<@Pattern(regexp = "[A-Za-z*]{1,40}") String> blocked) {}

    public record CreateRequest(@NotBlank @Size(max = 48) String name,
                                @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9.:_\\-]+") String address,
                                @Size(max = 300) String description, UUID manager) {}

    public record PremiumRequest(@Min(0) @Max(3650) int days) {}

    private final PartnerServerRepository partners;
    private final PlayerRepository players;

    public PartnerController(PartnerServerRepository partners, PlayerRepository players) {
        this.partners = partners;
        this.players = players;
    }

    /** Public – no sign-in needed (see SignedInCheck). */
    @GetMapping("/api/partners")
    public List<PartnerView> list() {
        return partners.findAllByOrderBySortOrderAscNameAsc().stream().map(p -> view(p, null)).toList();
    }

    /** The same list, marking the servers you manage (signed in). */
    @GetMapping("/api/partners/mine")
    public List<PartnerView> mine(@RequestAttribute(SignedInCheck.ME) UUID me) {
        return partners.findAllByOrderBySortOrderAscNameAsc().stream().map(p -> view(p, me)).toList();
    }

    @PutMapping("/api/partners/{id}/rules")
    @Transactional
    public PartnerView setRules(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id,
                                @Valid @RequestBody RulesRequest body) {
        PartnerServer partner = partners.findById(id).orElseThrow(() -> NexusException.notFound("No such partner server"));
        if (!me.getUuid().equals(partner.getManager()) && !me.isAdmin()) {
            throw NexusException.forbidden("Only this server's manager can change its rules");
        }
        if (body.blocked().size() > 60) {
            throw NexusException.badRequest("Too many mods listed");
        }
        partner.setBlockedMods(body.blocked());
        partners.save(partner);
        return view(partner, me.getUuid());
    }

    @PostMapping("/api/admin/partners")
    @Transactional
    public PartnerView create(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @Valid @RequestBody CreateRequest body) {
        requireAdmin(me);
        if (partners.existsByAddressIgnoreCase(body.address())) {
            throw NexusException.badRequest("That server is already a partner");
        }
        PartnerServer saved = partners.save(new PartnerServer(body.name(), body.address().toLowerCase(),
                body.description(), body.manager()));
        return view(saved, me.getUuid());
    }

    @DeleteMapping("/api/admin/partners/{id}")
    @Transactional
    public Map<String, Boolean> delete(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id) {
        requireAdmin(me);
        partners.deleteById(id);
        return Map.of("ok", true);
    }

    @PostMapping("/api/admin/players/{uuid}/premium")
    @Transactional
    public Map<String, Object> premium(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable UUID uuid,
                                       @Valid @RequestBody PremiumRequest body) {
        requireAdmin(me);
        Player target = players.findById(uuid).orElseThrow(() -> NexusException.notFound("No such player"));
        target.setPremiumUntil(body.days() == 0 ? null : Instant.now().plus(Duration.ofDays(body.days())));
        players.save(target);
        return Map.of("ok", true, "premiumUntil", String.valueOf(target.getPremiumUntil()));
    }

    private static PartnerView view(PartnerServer p, UUID viewer) {
        return new PartnerView(p.getId(), p.getName(), p.getAddress(), p.getDescription(), p.getBlockedMods(),
                viewer != null && viewer.equals(p.getManager()));
    }

    private static void requireAdmin(Player me) {
        if (!me.isAdmin()) {
            throw NexusException.forbidden("Admins only");
        }
    }
}
