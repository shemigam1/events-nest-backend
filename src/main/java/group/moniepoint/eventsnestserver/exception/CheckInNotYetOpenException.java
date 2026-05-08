package group.moniepoint.eventsnestserver.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CheckInNotYetOpenException extends InvalidEventStateException {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public CheckInNotYetOpenException(LocalDateTime opensAt) {
        super("check-in is not yet open, it opens at " + opensAt.format(FMT));
    }
}
