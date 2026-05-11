package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

/**
 * Bad request — image rejected by the validator (wrong content type, too
 * big, or magic bytes don't match the declared content type). Maps to 400.
 */
public class InvalidEventImageException extends EventsNestException {
    public InvalidEventImageException(String message) {
        super(message);
    }
}
