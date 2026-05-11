package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

/**
 * Every event must have at least one day. Deleting the last remaining day
 * is rejected; the organiser should delete the event itself instead.
 */
public class CannotDeleteLastEventDayException extends InvalidEventStateException {
    public CannotDeleteLastEventDayException() {
        super("cannot delete the only day of an event");
    }
}
