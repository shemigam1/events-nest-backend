package group.moniepoint.eventsnestserver.exception;

public class NotEventOrganizerException extends UnauthorizedException {
    public NotEventOrganizerException() {
        super("only the event organizer can perform this action");
    }
}
