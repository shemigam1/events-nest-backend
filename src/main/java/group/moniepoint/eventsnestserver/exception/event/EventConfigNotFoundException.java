package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

/**
 * Thrown when an event has no config row. Indicates a data integrity issue —
 * every event should auto-create a config on creation. If this surfaces in
 * production, check whether an event was created before V3 backfilled or
 * before the auto-create wiring landed.
 */
public class EventConfigNotFoundException extends ResourceNotFoundException {
    public EventConfigNotFoundException() {
        super("event config not found");
    }
}
