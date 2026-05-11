package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class EventDayNotFoundException extends ResourceNotFoundException {
    public EventDayNotFoundException() {
        super("event day not found");
    }
}
