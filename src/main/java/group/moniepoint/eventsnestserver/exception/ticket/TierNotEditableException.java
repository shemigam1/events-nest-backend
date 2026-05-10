package group.moniepoint.eventsnestserver.exception.ticket;

import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;

public class TierNotEditableException extends InvalidEventStateException {
    public TierNotEditableException(int soldCount) {
        super(soldCount + " booking(s) exist for this tier — it cannot be edited");
    }
}
