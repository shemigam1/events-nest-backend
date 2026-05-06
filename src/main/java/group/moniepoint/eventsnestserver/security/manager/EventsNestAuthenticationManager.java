package group.moniepoint.eventsnestserver.security.manager;

import group.moniepoint.eventsnestserver.security.exception.InvalidAuthenticationMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EventsNestAuthenticationManager implements AuthenticationManager {

    private final List<AuthenticationProvider> authenticationProviders;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        return authenticationProviders.stream()
                .filter(provider -> provider.supports(authentication.getClass()))
                .findFirst()
                .orElseThrow(() -> new InvalidAuthenticationMethod("no provider supports this authentication type"))
                .authenticate(authentication);
    }
}
