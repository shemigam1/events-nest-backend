package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class EventFieldLockedException extends EventsNestException {
    public EventFieldLockedException(String field) {
        super("'" + field + "' cannot be edited on a published event");
    }
}
