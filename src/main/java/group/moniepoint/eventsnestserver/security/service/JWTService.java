package group.moniepoint.eventsnestserver.security.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

public interface JWTService {
    String generateAccessToken(Authentication authentication);

    UserDetails validate(String accessToken);
}
