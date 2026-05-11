package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class EventNotSubmittableException extends InvalidEventStateException {
    public EventNotSubmittableException() {
        super("only DRAFT events can be submitted for approval");
    }
}
