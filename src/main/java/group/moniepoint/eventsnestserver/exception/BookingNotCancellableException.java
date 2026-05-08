package group.moniepoint.eventsnestserver.exception;

public class BookingNotCancellableException extends InvalidEventStateException {
    public BookingNotCancellableException() {
        super("only confirmed bookings can be cancelled");
    }
}
