package com.nexusclient.backend.friends;

import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * FriendController – the friends web addresses (all need a signed-in player):
 *   GET  /api/friends                       friends (with online status/server), requests, blocks
 *   POST /api/friends/request {name}        send a request (or accept theirs)
 *   POST /api/friends/accept  {uuid}
 *   POST /api/friends/decline {uuid}        say no, or take back your own request
 *   POST /api/friends/remove  {uuid}
 *   POST /api/friends/block   {uuid}
 *   POST /api/friends/unblock {uuid}
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    public record NameRequest(@NotBlank String name) {}

    public record PlayerRequest(@NotNull UUID uuid) {}

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping
    public FriendService.Overview overview(@RequestAttribute(SignedInCheck.ME) UUID me) {
        return friends.overview(me);
    }

    @PostMapping("/request")
    public Map<String, String> request(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody NameRequest body) {
        return Map.of("result", friends.request(me, body.name()));
    }

    @PostMapping("/accept")
    public Map<String, Boolean> accept(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        friends.accept(me, body.uuid());
        return Map.of("ok", true);
    }

    @PostMapping("/decline")
    public Map<String, Boolean> decline(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        friends.decline(me, body.uuid());
        return Map.of("ok", true);
    }

    @PostMapping("/remove")
    public Map<String, Boolean> remove(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        friends.remove(me, body.uuid());
        return Map.of("ok", true);
    }

    @PostMapping("/block")
    public Map<String, Boolean> block(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        friends.block(me, body.uuid());
        return Map.of("ok", true);
    }

    @PostMapping("/unblock")
    public Map<String, Boolean> unblock(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody PlayerRequest body) {
        friends.unblock(me, body.uuid());
        return Map.of("ok", true);
    }
}
