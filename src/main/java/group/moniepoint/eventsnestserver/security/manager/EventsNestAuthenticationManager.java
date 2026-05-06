package group.moniepoint.eventsnestserver.security.manager;

import group.moniepoint.eventsnestserver.security.exception.InvalidAuthenticationMethod;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class EventsNestAuthenticationManager implements AuthenticationManager {
    private final List<AuthenticationProvider> authenticationProviders;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AuthenticationProvider authenticationProvider = getAuthenticationProvider(authentication);
        return authenticationProvider.authenticate(authentication);
    }

    private AuthenticationProvider getAuthenticationProvider(Authentication authentication) {
        return authenticationProviders.stream()
                .filter(provider -> provider.supports(authentication.getClass()))
                .findFirst()
                .orElseThrow(() -> new InvalidAuthenticationMethod(""));
    }
}
