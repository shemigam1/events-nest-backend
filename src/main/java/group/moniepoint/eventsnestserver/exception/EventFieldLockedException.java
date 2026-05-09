package group.moniepoint.eventsnestserver.exception;

public class EventFieldLockedException extends EventsNestException {
    public EventFieldLockedException(String field) {
        super("'" + field + "' cannot be edited on a published event");
    }
}
