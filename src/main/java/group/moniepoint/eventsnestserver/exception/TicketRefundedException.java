package group.moniepoint.eventsnestserver.exception;

public class TicketRefundedException extends InvalidEventStateException {
    public TicketRefundedException() {
        super("ticket has been refunded and is no longer valid");
    }
}
