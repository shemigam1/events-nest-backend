package group.moniepoint.eventsnestserver.security.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import group.moniepoint.eventsnestserver.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring("Bearer ".length());
            DecodedJWT jwt = jwtService.validateAccessToken(token);

            String email = jwt.getClaim("email").asString();
            List<SimpleGrantedAuthority> authorities = Arrays.stream(jwt.getClaim("roles").asArray(String.class))
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(email, null, authorities));
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
