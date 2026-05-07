package group.moniepoint.eventsnestserver.events.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EndAfterStartValidator implements ConstraintValidator<EndAfterStart, HasStartEndTime> {

    @Override
    public boolean isValid(HasStartEndTime request, ConstraintValidatorContext ctx) {
        if (request.getStartTime() == null || request.getEndTime() == null) return true;
        return request.getEndTime().isAfter(request.getStartTime());
    }
}
