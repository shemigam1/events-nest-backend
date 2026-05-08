package group.moniepoint.eventsnestserver.exception;

public class PublishedEventNotEditableException extends InvalidEventStateException {
    public PublishedEventNotEditableException() {
        super("cannot update a published event");
    }
}
