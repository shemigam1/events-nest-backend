package group.moniepoint.eventsnestserver.exception.event;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class EventNotPendingApprovalException extends InvalidEventStateException {
    public EventNotPendingApprovalException() {
        super("event is not pending approval");
    }
}
