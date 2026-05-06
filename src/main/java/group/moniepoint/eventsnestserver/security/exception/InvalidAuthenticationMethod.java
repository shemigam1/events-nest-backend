package group.moniepoint.eventsnestserver.security.exception;

import org.springframework.security.core.AuthenticationException;

public class InvalidAuthenticationMethod extends AuthenticationException {
    public InvalidAuthenticationMethod(String message) {
        super(message);
    }
}
