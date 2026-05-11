package group.moniepoint.eventsnestserver.exception.booking;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class BookingNotFoundException extends ResourceNotFoundException {
    public BookingNotFoundException() {
        super("booking not found");
    }
}
