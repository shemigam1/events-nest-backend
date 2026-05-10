package group.moniepoint.eventsnestserver.exception.auth;

import group.moniepoint.eventsnestserver.exception.UnauthorizedException;

public class NotEventOrganizerException extends UnauthorizedException {
    public NotEventOrganizerException() {
        super("only the event organizer can perform this action");
    }
}
