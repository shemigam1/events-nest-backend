package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class EventNotFoundException extends ResourceNotFoundException {
    public EventNotFoundException() {
        super("event not found");
    }
}
