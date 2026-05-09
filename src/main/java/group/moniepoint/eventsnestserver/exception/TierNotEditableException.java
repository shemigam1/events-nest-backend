package group.moniepoint.eventsnestserver.exception;

public class TierNotEditableException extends InvalidEventStateException {
    public TierNotEditableException(int soldCount) {
        super(soldCount + " booking(s) exist for this tier — it cannot be edited");
    }
}
