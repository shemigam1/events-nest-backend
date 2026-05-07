package group.moniepoint.eventsnestserver.events.common.validation;

import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EndAfterStartValidatorTest {

    private EndAfterStartValidator validator;
    private CreateEventRequest request;

    @BeforeEach
    void setUp() {
        validator = new EndAfterStartValidator();
        request = new CreateEventRequest();
    }

    @Test
    void isValidReturnsTrueWhenStartTimeIsNull() {
        request.setStartTime(null);
        request.setEndTime(LocalDateTime.now().plusHours(1));

        assertThat(validator.isValid(request, null)).isTrue();
    }

    @Test
    void isValidReturnsTrueWhenEndTimeIsNull() {
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(null);

        assertThat(validator.isValid(request, null)).isTrue();
    }

    @Test
    void isValidReturnsTrueWhenBothTimesAreNull() {
        request.setStartTime(null);
        request.setEndTime(null);

        assertThat(validator.isValid(request, null)).isTrue();
    }

    @Test
    void isValidReturnsTrueWhenEndTimeIsAfterStartTime() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        request.setStartTime(start);
        request.setEndTime(start.plusHours(2));

        assertThat(validator.isValid(request, null)).isTrue();
    }

    @Test
    void isValidReturnsFalseWhenEndTimeEqualsStartTime() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 1, 10, 0);
        request.setStartTime(time);
        request.setEndTime(time);

        assertThat(validator.isValid(request, null)).isFalse();
    }

    @Test
    void isValidReturnsFalseWhenEndTimeIsBeforeStartTime() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        request.setStartTime(start);
        request.setEndTime(start.minusHours(1));

        assertThat(validator.isValid(request, null)).isFalse();
    }
}
