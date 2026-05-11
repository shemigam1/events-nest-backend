package group.moniepoint.eventsnestserver.exception.checkin;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CheckInClosedForDayException extends InvalidEventStateException {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public CheckInClosedForDayException(LocalDateTime dayEndsAt) {
        super("check-in for this day is closed (day ended at " + dayEndsAt.format(FMT) + ")");
    }
}
