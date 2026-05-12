package group.moniepoint.eventsnestserver.exception.guestlist;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class GuestNotFoundException extends ResourceNotFoundException {
    public GuestNotFoundException() {
        super("guest not found");
    }
}
