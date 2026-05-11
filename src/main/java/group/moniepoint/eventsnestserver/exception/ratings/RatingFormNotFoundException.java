package group.moniepoint.eventsnestserver.exception.ratings;

import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;

public class RatingFormNotFoundException extends ResourceNotFoundException {
    public RatingFormNotFoundException() {
        super("rating form not found");
    }
}
