package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class NoPendingEventUpdateException extends EventsNestException {
    public NoPendingEventUpdateException() {
        super("this event has no pending update to review");
    }
}
