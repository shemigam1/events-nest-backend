package group.moniepoint.eventsnestserver.events.dto.response;

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
public class EventDayResponse {
    private UUID id;
    private UUID eventId;
    private Integer dayNumber;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime checkInStartTime;
    private String venue;
}
