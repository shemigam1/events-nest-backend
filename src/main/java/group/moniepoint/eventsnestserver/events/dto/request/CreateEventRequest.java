package group.moniepoint.eventsnestserver.events.dto.request;

import group.moniepoint.eventsnestserver.events.common.validation.EndAfterStart;
import group.moniepoint.eventsnestserver.events.common.validation.HasStartEndTime;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@EndAfterStart
public class CreateEventRequest implements HasStartEndTime {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 255)
    private String venue;

    @NotNull
    @Future
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    private LocalDateTime checkInStartTime;
}
