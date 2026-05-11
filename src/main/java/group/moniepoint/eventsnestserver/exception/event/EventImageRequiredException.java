package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

/**
 * Thrown when an organizer tries to submit an event for approval before
 * uploading a cover image (PRD §5.4).
 */
public class EventImageRequiredException extends InvalidEventStateException {
    public EventImageRequiredException() {
        super("event must have a cover image before it can be submitted for approval");
    }
}
