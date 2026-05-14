package group.moniepoint.eventsnestserver.exception.booking;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class BookingNotCancellableException extends InvalidEventStateException {
    public BookingNotCancellableException() {
        super("booking cannot be cancelled");
    }
}
