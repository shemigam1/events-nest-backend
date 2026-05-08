package group.moniepoint.eventsnestserver.exception;

public class EventAlreadyCancelledException extends InvalidEventStateException {
    public EventAlreadyCancelledException() {
        super("event is already cancelled");
    }
}
