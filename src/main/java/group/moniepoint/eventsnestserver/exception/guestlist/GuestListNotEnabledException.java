package group.moniepoint.eventsnestserver.exception.guestlist;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class GuestListNotEnabledException extends InvalidEventStateException {
    public GuestListNotEnabledException() {
        super("guest list is not enabled for this event");
    }
}
