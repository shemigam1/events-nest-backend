package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class EventNotPublishedException extends EventsNestException {
    public EventNotPublishedException() {
        super("event is not publicly available");
    }
}
