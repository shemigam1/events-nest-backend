package group.moniepoint.eventsnestserver.exception.auth;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class EmailAlreadyInUseException extends EventsNestException {
    public EmailAlreadyInUseException() {
        super("Email cannot be used");
    }
}
