package group.moniepoint.eventsnestserver.exception;

public class EventNotDeletableException extends InvalidEventStateException {
    public EventNotDeletableException() {
        super("only DRAFT or CANCELLED events can be deleted");
    }
}
