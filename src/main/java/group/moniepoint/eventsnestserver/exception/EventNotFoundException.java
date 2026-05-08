package group.moniepoint.eventsnestserver.exception;

public class EventNotFoundException extends ResourceNotFoundException {
    public EventNotFoundException() {
        super("event not found");
    }
}
