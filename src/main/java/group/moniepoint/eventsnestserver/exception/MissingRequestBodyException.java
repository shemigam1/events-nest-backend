package group.moniepoint.eventsnestserver.exception;

public class MissingRequestBodyException extends EventsNestException {
    public MissingRequestBodyException() {
        super("request body is required but was not provided");
    }
}
