package group.moniepoint.eventsnestserver.events.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotPastDateValidator.class)
public @interface NotPastDate {
    String message() default "event start date cannot be in the past";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
