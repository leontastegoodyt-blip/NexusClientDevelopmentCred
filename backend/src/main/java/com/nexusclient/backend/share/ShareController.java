package com.nexusclient.backend.share;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ShareController – short share codes instead of the long NX1-/NXC-/NXH- codes.
 *   POST /api/share {kind, data}   → {"code": "NX-7K3P-9QWD"}   (max 64 KB, 50 codes a day)
 *   GET  /api/share/{code}         → {"kind": "...", "data": "..."}
 * The data is whatever the launcher/mod shares (e.g. an NX1- setup code); the server doesn't read it.
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    /** Letters and numbers that can't be mixed up (no 0/O, 1/I/L). */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    public record CreateRequest(@NotNull ShareCode.Kind kind, @NotBlank @Size(max = 65536) String data) {}

    private final SecureRandom random = new SecureRandom();
    private final ShareCodeRepository codes;

    public ShareController(ShareCodeRepository codes) {
        this.codes = codes;
    }

    @PostMapping
    public Map<String, String> create(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody CreateRequest body) {
        if (codes.countByOwnerAndCreatedAtAfter(me, Instant.now().minus(Duration.ofDays(1))) >= 50) {
            throw NexusException.badRequest("You've made a lot of share codes today – try again tomorrow");
        }
        String code;
        do {
            code = "NX-" + randomPart() + "-" + randomPart();
        } while (codes.existsById(code));
        codes.save(new ShareCode(code, body.kind(), body.data(), me));
        return Map.of("code", code);
    }

    @GetMapping("/{code}")
    public Map<String, String> get(@PathVariable String code) {
        ShareCode found = codes.findById(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> NexusException.notFound("That share code doesn't exist"));
        return Map.of("kind", found.getKind(), "data", found.getData());
    }

    private String randomPart() {
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            part.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return part.toString();
    }
}
