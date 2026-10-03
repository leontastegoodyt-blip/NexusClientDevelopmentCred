package com.nexusclient.backend.party;

import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PartyController – the party web addresses:
 *   GET  /api/party                         your party (or {"party": null})
 *   POST /api/party/invite  {uuid}          invite a friend
 *   POST /api/party/accept  {partyId}
 *   POST /api/party/leave
 *   POST /api/party/kick    {uuid}          leader only
 *   POST /api/party/promote {uuid}          leader only: make someone else leader
 *   POST /api/party/chat    {text}
 *   POST /api/party/waypoint {x, y, z, dimension, label}
 *   POST /api/party/warp    {server}        leader only: everyone gets "join <server>"
 */
@RestController
@RequestMapping("/api/party")
public class PartyController {

    public record PlayerRequest(@NotNull UUID uuid) {}

    public record AcceptRequest(@NotBlank String partyId) {}

    public record ChatRequest(@NotBlank @Size(max = 256) String text) {}

    public record WaypointRequest(double x, double y, double z, @Size(max = 64) String dimension,
                                  @Size(max = 32) String label) {}

    public record WarpRequest(@NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9.:_\\-]+") String server) {}

    private static final Map<String, Boolean> OK = Map.of("ok", true);
    private final PartyService parties;

    public PartyController(PartyService parties) {
        this.parties = parties;
    }

    @GetMapping
    public Map<String, Object> current(@RequestAttribute(SignedInCheck.ME) UUID me) {
        Map<String, Object> result = new HashMap<>();
        result.put("party", parties.current(me));
        return result;
    }

    @PostMapping("/invite")
    public Map<String, Boolean> invite(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        parties.invite(me, body.uuid());
        return OK;
    }

    @PostMapping("/accept")
    public Map<String, Boolean> accept(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody AcceptRequest body) {
        parties.accept(me, body.partyId());
        return OK;
    }

    @PostMapping("/leave")
    public Map<String, Boolean> leave(@RequestAttribute(SignedInCheck.ME) UUID me) {
        parties.leave(me);
        return OK;
    }

    @PostMapping("/kick")
    public Map<String, Boolean> kick(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        parties.kick(me, body.uuid());
        return OK;
    }

    @PostMapping("/promote")
    public Map<String, Boolean> promote(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        parties.promote(me, body.uuid());
        return OK;
    }

    @PostMapping("/chat")
    public Map<String, Boolean> chat(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @Valid @RequestBody ChatRequest body) {
        parties.chat(me, body.text());
        return OK;
    }

    @PostMapping("/waypoint")
    public Map<String, Boolean> waypoint(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody WaypointRequest body) {
        parties.waypoint(me, body.x(), body.y(), body.z(), body.dimension(), body.label());
        return OK;
    }

    @PostMapping("/warp")
    public Map<String, Boolean> warp(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody WarpRequest body) {
        parties.warp(me, body.server());
        return OK;
    }
}
