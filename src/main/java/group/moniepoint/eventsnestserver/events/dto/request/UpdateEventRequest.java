package group.moniepoint.eventsnestserver.events.dto.request;

import group.moniepoint.eventsnestserver.events.common.validation.EndAfterStart;
import group.moniepoint.eventsnestserver.events.common.validation.HasStartEndTime;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@EndAfterStart
public class UpdateEventRequest implements HasStartEndTime {

    @Size(max = 255)
    private String title;

    private String description;

    @Size(max = 255)
    private String venue;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime checkInStartTime;
}
