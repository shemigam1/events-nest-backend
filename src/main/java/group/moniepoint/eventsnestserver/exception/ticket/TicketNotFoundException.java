package group.moniepoint.eventsnestserver.exception.ticket;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class TicketNotFoundException extends ResourceNotFoundException {
    public TicketNotFoundException() {
        super("ticket not found");
    }
}
