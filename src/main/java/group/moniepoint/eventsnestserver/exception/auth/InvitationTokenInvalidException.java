package group.moniepoint.eventsnestserver.exception.auth;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class InvitationTokenInvalidException extends EventsNestException {
    public InvitationTokenInvalidException() {
        super("invitation link is invalid or has expired");
    }
}
