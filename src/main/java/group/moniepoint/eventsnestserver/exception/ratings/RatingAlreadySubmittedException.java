package group.moniepoint.eventsnestserver.exception.ratings;

import group.moniepoint.eventsnestserver.exception.EventsNestException;

public class RatingAlreadySubmittedException extends EventsNestException {
    public RatingAlreadySubmittedException() {
        super("you have already submitted a rating for this event");
    }
}
