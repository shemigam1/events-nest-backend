package group.moniepoint.eventsnestserver.exception;

public class InsufficientTierCapacityException extends InvalidEventStateException {
    public InsufficientTierCapacityException() {
        super("insufficient capacity for this tier");
    }
}
