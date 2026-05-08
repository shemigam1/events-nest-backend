package group.moniepoint.eventsnestserver.exception;

public class TicketAlreadyUsedException extends InvalidEventStateException {
    public TicketAlreadyUsedException() {
        super("ticket has already been checked in");
    }
}
