package group.moniepoint.eventsnestserver.exception.checkin;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class InvalidCheckInStartTimeException extends EventsNestException {
    public InvalidCheckInStartTimeException() {
        super("check-in start time must be before event start time");
    }
}
