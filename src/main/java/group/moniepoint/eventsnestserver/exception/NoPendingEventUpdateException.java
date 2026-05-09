package group.moniepoint.eventsnestserver.exception;

public class NoPendingEventUpdateException extends EventsNestException {
    public NoPendingEventUpdateException() {
        super("this event has no pending update to review");
    }
}
