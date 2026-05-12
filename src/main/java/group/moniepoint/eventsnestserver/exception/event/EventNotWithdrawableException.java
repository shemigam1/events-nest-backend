package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

/**
 * Thrown when an organiser tries to withdraw a submission that's not
 * currently awaiting review. Only PENDING_APPROVAL events can be
 * withdrawn back to DRAFT.
 */
public class EventNotWithdrawableException extends InvalidEventStateException {
    public EventNotWithdrawableException() {
        super("only events pending approval can be withdrawn");
    }
}
