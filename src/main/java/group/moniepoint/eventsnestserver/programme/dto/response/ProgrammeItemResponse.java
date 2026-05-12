package group.moniepoint.eventsnestserver.programme.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammeItemResponse {
    private UUID id;
    private UUID eventId;
    private UUID eventDayId;
    private String title;
    private String description;
    private String speakerName;
    private String speakerBio;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int displayOrder;
}
