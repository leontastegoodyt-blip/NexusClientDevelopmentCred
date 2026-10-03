package com.nexusclient.backend.account;

import com.nexusclient.backend.NexusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SignedInCheck – every /api/ request (except signing in) must carry "Authorization: Bearer <token>".
 * The signed-in player's UUID is then available to controllers as @RequestAttribute(ME).
 */
@Configuration
public class SignedInCheck implements HandlerInterceptor, WebMvcConfigurer {

    /** The request attribute holding the signed-in player's UUID. */
    public static final String ME = "nexus.player";
    /** Holds the whole Player (for role checks). */
    public static final String ME_PLAYER = "nexus.playerObject";

    private final AuthService auth;

    public SignedInCheck(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/challenge", "/api/auth/verify", "/api/auth/dev", "/api/auth/logout",
                        "/api/partners", "/api/cosmetics/worn", "/api/crash-reports"); // public: the mod reads these without signing in
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        Player player = auth.playerFor(token)
                .orElseThrow(() -> NexusException.unauthorized("Please sign in again"));
        request.setAttribute(ME, player.getUuid());
        request.setAttribute(ME_PLAYER, player);
        return true;
    }
}
