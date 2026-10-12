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
 * Sign-in is limited: 30 tries per 10 minutes per internet address, 10 per username (RateLimiter).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** A Minecraft username: 3–16 letters, numbers or underscores. */
    private static final String NAME = "^[A-Za-z0-9_]{3,16}$";

    public record VerifyRequest(@NotBlank @Pattern(regexp = NAME) String username, @NotBlank String serverId) {}

    public record DevRequest(@NotBlank @Pattern(regexp = NAME) String name) {}

    /** Sign-in attempts allowed per internet address / per username, every 10 minutes (stops guessing and floods). */
    private static final int PER_ADDRESS = 30;
    private static final int PER_NAME = 10;
    private static final java.time.Duration WINDOW = java.time.Duration.ofMinutes(10);
    private static final String SLOW_DOWN = "Too many sign-in attempts – wait a few minutes and try again";

    private final AuthService auth;
    private final com.nexusclient.backend.RateLimiter limits;

    public AuthController(AuthService auth, com.nexusclient.backend.RateLimiter limits) {
        this.auth = auth;
        this.limits = limits;
    }

    @PostMapping("/challenge")
    public Map<String, String> challenge(jakarta.servlet.http.HttpServletRequest http) {
        limits.check("signin-ip:" + http.getRemoteAddr(), PER_ADDRESS, WINDOW, SLOW_DOWN);
        return Map.of("serverId", auth.challenge());
    }

    @PostMapping("/verify")
    public AuthService.SignedIn verify(jakarta.servlet.http.HttpServletRequest http, @Valid @RequestBody VerifyRequest request) {
        limits.check("signin-ip:" + http.getRemoteAddr(), PER_ADDRESS, WINDOW, SLOW_DOWN);
        limits.check("signin-name:" + request.username().toLowerCase(java.util.Locale.ROOT), PER_NAME, WINDOW, SLOW_DOWN);
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
