package group.moniepoint.eventsnestserver.programme.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateProgrammeItemRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String description;
    private String speakerName;
    private String speakerBio;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int displayOrder;

    /** Null means the item spans the whole event (not day-specific). */
    private UUID eventDayId;
}
