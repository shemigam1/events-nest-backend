package group.moniepoint.eventsnestserver.events.dto.request;

import group.moniepoint.eventsnestserver.events.common.validation.EndAfterStart;
import group.moniepoint.eventsnestserver.events.common.validation.HasStartEndTime;
import group.moniepoint.eventsnestserver.events.common.validation.NotPastDate;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@EndAfterStart
public class CreateEventRequest implements HasStartEndTime {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotBlank
    @Size(max = 255)
    private String venue;

    @NotNull
    @NotPastDate
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    private LocalDateTime checkInStartTime;

    @Valid
    @NotEmpty(message = "at least one ticket tier is required")
    private List<CreateTicketTierRequest> tiers;
}
