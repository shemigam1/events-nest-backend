package group.moniepoint.eventsnestserver.exception;

public class InvalidCheckInStartTimeException extends EventsNestException {
    public InvalidCheckInStartTimeException() {
        super("check-in start time must be before event start time");
    }
}
