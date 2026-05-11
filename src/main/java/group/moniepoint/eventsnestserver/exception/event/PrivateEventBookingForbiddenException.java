package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.UnauthorizedException;

/**
 * Thrown when someone tries to book a ticket for a PRIVATE event without
 * holding an accepted RSVP (PRD §5.5).
 */
public class PrivateEventBookingForbiddenException extends UnauthorizedException {
    public PrivateEventBookingForbiddenException() {
        super("this event is invite-only — an accepted RSVP is required to book");
    }
}
