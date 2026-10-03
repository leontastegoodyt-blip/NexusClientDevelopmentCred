package com.nexusclient.backend.live;

import com.nexusclient.backend.account.AuthService;
import com.nexusclient.backend.account.Player;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * LiveConfig – opens the WebSocket at /ws. Connecting needs the same sign-in token as the API,
 * in the "Authorization: Bearer <token>" header; without a valid one the connection is refused.
 */
@Configuration
@EnableWebSocket
public class LiveConfig implements WebSocketConfigurer, HandshakeInterceptor {

    private final LiveHub hub;
    private final AuthService auth;

    public LiveConfig(LiveHub hub, AuthService auth) {
        this.hub = hub;
        this.auth = auth;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub, "/ws").addInterceptors(this);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        Optional<Player> player = auth.playerFor(token);
        if (player.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(LiveHub.PLAYER, player.get().getUuid());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // nothing to do
    }
}
