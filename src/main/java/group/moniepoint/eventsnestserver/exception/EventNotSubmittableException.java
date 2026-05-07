package group.moniepoint.eventsnestserver.exception;

public class EventNotSubmittableException extends InvalidEventStateException {
    public EventNotSubmittableException() {
        super("only DRAFT events can be submitted for approval");
    }
}
