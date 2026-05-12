package group.moniepoint.eventsnestserver.exception.guestlist;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class InvalidRsvpTokenException extends EventsNestException {
    public InvalidRsvpTokenException() {
        super("invalid or expired RSVP token");
    }
}
