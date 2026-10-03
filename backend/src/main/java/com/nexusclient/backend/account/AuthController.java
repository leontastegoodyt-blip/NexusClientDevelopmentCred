package com.nexusclient.backend.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AuthController – the sign-in web addresses:
 *   POST /api/auth/challenge            → {"serverId": "..."}
 *   POST /api/auth/verify {username, serverId} → {"token", "uuid", "name", "expiresAt"}
 *   POST /api/auth/dev {name}           → same, development servers only
 *   POST /api/auth/logout               (with the token) → signs this session out
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** A Minecraft username: 3–16 letters, numbers or underscores. */
    private static final String NAME = "^[A-Za-z0-9_]{3,16}$";

    public record VerifyRequest(@NotBlank @Pattern(regexp = NAME) String username, @NotBlank String serverId) {}

    public record DevRequest(@NotBlank @Pattern(regexp = NAME) String name) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/challenge")
    public Map<String, String> challenge() {
        return Map.of("serverId", auth.challenge());
    }

    @PostMapping("/verify")
    public AuthService.SignedIn verify(@Valid @RequestBody VerifyRequest request) {
        return auth.verify(request.username(), request.serverId());
    }

    @PostMapping("/dev")
    public AuthService.SignedIn dev(@Valid @RequestBody DevRequest request) {
        return auth.devSignIn(request.name());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            auth.signOut(header.substring(7));
        }
        return Map.of("ok", true);
    }
}
