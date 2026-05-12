package group.moniepoint.eventsnestserver.exception.checkin;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class WrongTicketEventDayException extends InvalidEventStateException {
    public WrongTicketEventDayException() {
        super("this ticket is not valid for the selected event day");
    }
}
