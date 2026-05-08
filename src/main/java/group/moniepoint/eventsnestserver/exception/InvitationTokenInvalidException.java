package group.moniepoint.eventsnestserver.exception;

public class InvitationTokenInvalidException extends EventsNestException {
    public InvitationTokenInvalidException() {
        super("invitation link is invalid or has expired");
    }
}
