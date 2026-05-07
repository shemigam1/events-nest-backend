package group.moniepoint.eventsnestserver.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JWTServiceImpl implements JWTService {

    private static final String ISSUER = "events-nest-server";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    @Value("${jwt.signing.key}")
    private String signingKey;

    @Value("${jwt.access-token.expiry-seconds:86400}")
    private long accessTokenExpirySeconds;

    @Value("${jwt.refresh-token.expiry-seconds:604800}")
    private long refreshTokenExpirySeconds;

    @Override
    public String generateAccessToken(Authentication authentication) {
        String email = authentication.getPrincipal().toString();
        String[] roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toArray(String[]::new);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim(CLAIM_EMAIL, email)
                .withArrayClaim(CLAIM_ROLES, roles)
                .withClaim(CLAIM_TYPE, TYPE_ACCESS)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plusSeconds(accessTokenExpirySeconds))
                .sign(algorithm());
    }

    @Override
    public String generateRefreshToken(Authentication authentication) {
        String email = authentication.getPrincipal().toString();

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim(CLAIM_TYPE, TYPE_REFRESH)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds))
                .sign(algorithm());
    }

    @Override
    public DecodedJWT validateAccessToken(String token) {
        return verify(token, TYPE_ACCESS);
    }

    @Override
    public DecodedJWT validateRefreshToken(String token) {
        return verify(token, TYPE_REFRESH);
    }

    private DecodedJWT verify(String token, String expectedType) {
        try {
            JWTVerifier verifier = JWT.require(algorithm())
                    .withIssuer(ISSUER)
                    .withClaim(CLAIM_TYPE, expectedType)
                    .build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new BadCredentialsException("invalid or expired token");
        }
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(signingKey);
    }
}
