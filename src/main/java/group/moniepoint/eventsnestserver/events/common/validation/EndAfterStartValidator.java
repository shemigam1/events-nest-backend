package group.moniepoint.eventsnestserver.events.common.validation;

import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EndAfterStartValidator implements ConstraintValidator<EndAfterStart, CreateEventRequest> {

    @Override
    public boolean isValid(CreateEventRequest createEventRequest, ConstraintValidatorContext constraintValidatorContext) {
        if (createEventRequest.getStartTime() == null || createEventRequest.getEndTime() == null) return true;
        return createEventRequest.getEndTime().isAfter(createEventRequest.getStartTime());
    }
}
