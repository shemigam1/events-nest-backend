package group.moniepoint.eventsnestserver.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class JWTServiceImpl implements JWTService {
    @Value("${jwt.signing.key}")
    private String signingKey;
    private final UserDetailsService userDetailsService;

    @Override
    public String generateAccessToken(Authentication authentication) {
        String username = authentication.getPrincipal().toString();

        String[] authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toArray(String[]::new);

        return JWT.create().withSubject(username)
                .withIssuer("events-nest-server")
                .withClaim("username", username)
                .withExpiresAt(Instant.now().plusSeconds(86400))
                .withArrayClaim("roles", authorities)
                .sign(Algorithm.HMAC256(Base64.getEncoder().encodeToString(signingKey.getBytes())));
    }

    @Override
    public UserDetails validate(String accessToken) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(Base64.getEncoder().encodeToString(signingKey.getBytes())))
                .withIssuer("events-nest-server")
                .withClaimPresence("username")
                .withClaimPresence("roles")
                .build();
        DecodedJWT decodedJWT = verifier.verify(accessToken);
        String username = decodedJWT.getClaim("username").asString();
        return userDetailsService.loadUserByUsername(username);
    }
}
