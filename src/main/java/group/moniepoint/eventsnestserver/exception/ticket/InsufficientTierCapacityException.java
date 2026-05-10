package group.moniepoint.eventsnestserver.exception.ticket;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class InsufficientTierCapacityException extends InvalidEventStateException {
    public InsufficientTierCapacityException() {
        super("insufficient capacity for this tier");
    }
}
