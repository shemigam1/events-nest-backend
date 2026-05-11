package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class PublishedEventNotEditableException extends InvalidEventStateException {
    public PublishedEventNotEditableException() {
        super("cannot update a published event");
    }
}
