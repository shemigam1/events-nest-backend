package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class EventNotDeletableException extends InvalidEventStateException {
    public EventNotDeletableException() {
        super("only DRAFT or CANCELLED events can be deleted");
    }
}
