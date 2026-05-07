package group.moniepoint.eventsnestserver.exception;

public class BookingCancellationForbiddenException extends UnauthorizedException {
    public BookingCancellationForbiddenException() {
        super("only the booking attendee can cancel this booking");
    }
}
