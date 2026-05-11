package group.moniepoint.eventsnestserver.exception.checkin;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class CheckInEventDayRequiredException extends InvalidEventStateException {
    public CheckInEventDayRequiredException() {
        super("eventDayId is required for check-in when this event has multiple days and the ticket is valid for all days");
    }
}
