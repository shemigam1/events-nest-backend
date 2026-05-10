package group.moniepoint.eventsnestserver.exception.ticket;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class TicketTierNotFoundException extends ResourceNotFoundException {
    public TicketTierNotFoundException() {
        super("ticket tier not found");
    }
}
