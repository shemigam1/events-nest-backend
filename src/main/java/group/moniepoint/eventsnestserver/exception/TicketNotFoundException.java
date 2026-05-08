package group.moniepoint.eventsnestserver.exception;

public class TicketNotFoundException extends ResourceNotFoundException {
    public TicketNotFoundException() {
        super("ticket not found");
    }
}
