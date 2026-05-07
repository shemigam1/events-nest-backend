package group.moniepoint.eventsnestserver.exception;

public class EmailAlreadyInUseException extends EventsNestException {
    public EmailAlreadyInUseException() {
        super("Email cannot be used");
    }
}
