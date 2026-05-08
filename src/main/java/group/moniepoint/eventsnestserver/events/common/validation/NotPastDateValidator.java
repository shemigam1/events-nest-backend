package group.moniepoint.eventsnestserver.events.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NotPastDateValidator implements ConstraintValidator<NotPastDate, LocalDateTime> {

    @Override
    public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return !value.toLocalDate().isBefore(LocalDate.now());
    }
}
