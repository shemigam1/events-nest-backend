package group.moniepoint.eventsnestserver.exception.guestlist;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class DuplicateGuestEmailException extends EventsNestException {
    public DuplicateGuestEmailException() {
        super("a guest with this email has already been added to the event");
    }
}
