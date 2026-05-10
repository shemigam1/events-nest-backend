package group.moniepoint.eventsnestserver.exception.booking;

import group.moniepoint.eventsnestserver.exception.UnauthorizedException;

public class BookingCancellationForbiddenException extends UnauthorizedException {
    public BookingCancellationForbiddenException() {
        super("only the booking attendee can cancel this booking");
    }
}
