package group.moniepoint.eventsnestserver.exception.ticket;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class TicketAlreadyUsedException extends InvalidEventStateException {
    public TicketAlreadyUsedException() {
        super("ticket has already been checked in");
    }
}
