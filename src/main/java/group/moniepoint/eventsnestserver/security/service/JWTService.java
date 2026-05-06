package group.moniepoint.eventsnestserver.security.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.core.Authentication;

public interface JWTService {
    String generateAccessToken(Authentication authentication);
    String generateRefreshToken(Authentication authentication);
    DecodedJWT validateAccessToken(String token);
    DecodedJWT validateRefreshToken(String token);
}
