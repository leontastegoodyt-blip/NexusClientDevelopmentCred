package com.nexusclient.backend.messages;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.account.SignedInCheck;
import com.nexusclient.backend.friends.FriendService;
import com.nexusclient.backend.live.Event;
import com.nexusclient.backend.live.LiveHub;
import com.nexusclient.backend.moderation.ChatFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MessageController – direct messages (DMs). Friends-only by default; players can switch DMs off
 * completely. Blocked players, muted players and bad words are stopped (see moderation/).
 *   GET  /api/messages/unread               {sender uuid: count}
 *   GET  /api/messages/{uuid}?before=<id>   the conversation, 50 at a time, newest first
 *   POST /api/messages/{uuid} {text}        send (max 500 characters, 30 a minute)
 *   POST /api/messages/{uuid}/read          mark their messages as read
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    public record SendRequest(@NotBlank @Size(max = 500) String text) {}

    public record MessageInfo(long id, UUID from, UUID to, String text, String sentAt, boolean read) {
        static MessageInfo of(DirectMessage m) {
            return new MessageInfo(m.getId(), m.getFrom(), m.getTo(), m.getText(), m.getSentAt().toString(),
                    m.getReadAt() != null);
        }
    }

    private final DirectMessageRepository messages;
    private final PlayerRepository players;
    private final FriendService friends;
    private final ChatFilter filter;
    private final LiveHub live;

    public MessageController(DirectMessageRepository messages, PlayerRepository players, FriendService friends,
                             ChatFilter filter, LiveHub live) {
        this.messages = messages;
        this.players = players;
        this.friends = friends;
        this.filter = filter;
        this.live = live;
    }

    @GetMapping("/unread")
    public Map<String, Long> unread(@RequestAttribute(SignedInCheck.ME) UUID me) {
        return messages.unreadCounts(me).stream()
                .collect(Collectors.toMap(row -> row[0].toString(), row -> (Long) row[1]));
    }

    @GetMapping("/{other}")
    public List<MessageInfo> conversation(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable UUID other,
                                          @RequestParam(defaultValue = "9223372036854775807") long before) {
        return messages.conversation(me, other, before, PageRequest.of(0, 50)).stream().map(MessageInfo::of).toList();
    }

    @PostMapping("/{other}")
    @Transactional
    public MessageInfo send(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable UUID other,
                            @Valid @RequestBody SendRequest body) {
        if (me.isMuted()) {
            throw NexusException.forbidden("You're muted until " + me.getMutedUntil());
        }
        Player target = players.findById(other).orElseThrow(() -> NexusException.notFound("That player hasn't used Nexus"));
        if (friends.eitherBlocked(me.getUuid(), other)) {
            throw NexusException.forbidden("You can't message " + target.getName());
        }
        boolean allowed = switch (target.getDmPolicy()) {
            case FRIENDS -> friends.areFriends(me.getUuid(), other);
            case NOBODY -> false;
        };
        if (!allowed) {
            throw NexusException.forbidden(target.getName() + " only gets messages from friends");
        }
        if (messages.countByFromAndSentAtAfter(me.getUuid(), Instant.now().minus(Duration.ofMinutes(1))) >= 30) {
            throw NexusException.badRequest("Slow down a little");
        }
        DirectMessage saved = messages.save(new DirectMessage(me.getUuid(), other, filter.clean(body.text().strip())));
        MessageInfo info = MessageInfo.of(saved);
        live.send(other, Event.of("dm").withObject("message", info).with("fromName", me.getName()));
        return info;
    }

    @PostMapping("/{other}/read")
    @Transactional
    public Map<String, Integer> read(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable UUID other) {
        return Map.of("marked", messages.markRead(me, other, Instant.now()));
    }
}
