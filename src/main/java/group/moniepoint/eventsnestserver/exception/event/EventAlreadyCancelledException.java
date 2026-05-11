package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class EventAlreadyCancelledException extends InvalidEventStateException {
    public EventAlreadyCancelledException() {
        super("event is already cancelled");
    }
}
