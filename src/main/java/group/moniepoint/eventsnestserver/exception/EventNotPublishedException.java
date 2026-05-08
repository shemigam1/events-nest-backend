package group.moniepoint.eventsnestserver.exception;

public class EventNotPublishedException extends EventsNestException {
    public EventNotPublishedException() {
        super("event is not publicly available");
    }
}
