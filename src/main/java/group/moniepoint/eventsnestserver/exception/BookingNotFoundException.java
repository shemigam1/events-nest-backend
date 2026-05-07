package group.moniepoint.eventsnestserver.exception;

public class BookingNotFoundException extends ResourceNotFoundException {
    public BookingNotFoundException() {
        super("booking not found");
    }
}
