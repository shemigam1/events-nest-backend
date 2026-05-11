package group.moniepoint.eventsnestserver.events.dto.request;

import group.moniepoint.eventsnestserver.events.common.validation.EndAfterStart;
import group.moniepoint.eventsnestserver.events.common.validation.HasStartEndTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EndAfterStart
public class CreateEventDayRequest implements HasStartEndTime {

    @Size(max = 255)
    private String title;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    private LocalDateTime checkInStartTime;

    /** Optional venue override. Null means "use the event's default venue". */
    @Size(max = 255)
    private String venue;
}
