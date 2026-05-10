package group.moniepoint.eventsnestserver.exception.ticket;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class TicketRefundedException extends InvalidEventStateException {
    public TicketRefundedException() {
        super("ticket has been refunded and is no longer valid");
    }
}
