package group.moniepoint.eventsnestserver.events.models;

/**
 * Controls whether an event surfaces on the public browse list and who can
 * book tickets for it. See PRD §5.5.
 *
 * PUBLIC  — listed on /events; anyone can book.
 * PRIVATE — not listed; bookable only by guests whose RSVP is ACCEPTED.
 *           Direct deep links via /events/code/{code} still work — the code
 *           is itself the invite.
 */
public enum EventVisibility {
    PUBLIC,
    PRIVATE
}
