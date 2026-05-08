package group.moniepoint.eventsnestserver.exception;

public class EventNotPendingApprovalException extends InvalidEventStateException {
    public EventNotPendingApprovalException() {
        super("event is not pending approval");
    }
}
