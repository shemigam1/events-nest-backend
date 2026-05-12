package group.moniepoint.eventsnestserver.programme.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProgrammeItemRequest {
    private String title;
    private String description;
    private String speakerName;
    private String speakerBio;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer displayOrder;
    private UUID eventDayId;
}
