package group.moniepoint.eventsnestserver.exception;

public class TicketTierNotFoundException extends ResourceNotFoundException {
    public TicketTierNotFoundException() {
        super("ticket tier not found");
    }
}
