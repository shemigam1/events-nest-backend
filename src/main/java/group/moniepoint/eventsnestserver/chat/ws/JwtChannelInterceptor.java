package group.moniepoint.eventsnestserver.chat.ws;

import com.auth0.jwt.interfaces.DecodedJWT;
import group.moniepoint.eventsnestserver.security.service.JWTService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates the JWT on every STOMP CONNECT frame.
 *
 * Flow:
 *   1. Client connects: STOMP CONNECT  Authorization: Bearer <token>
 *   2. Token is validated using the same JWTService as the REST layer.
 *   3. On success, a Spring Security principal is set on the WS session.
 *   4. On failure, the connection is refused with an exception.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JWTService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException(
                    "Missing or malformed Authorization header on STOMP CONNECT");
        }

        String token = authHeader.substring(7);
        DecodedJWT decoded = jwtService.validateAccessToken(token);

        String email = decoded.getSubject();
        List<String> roles = decoded.getClaim("roles").asList(String.class);
        List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
                : roles.stream().map(SimpleGrantedAuthority::new).toList();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(email, null, authorities);
        accessor.setUser(auth);

        log.debug("WebSocket CONNECT authenticated: {}", email);
        return message;
    }
}
