package group.moniepoint.eventsnestserver.events.dto.response;

import group.moniepoint.eventsnestserver.events.models.EventStatus;
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
public class EventSummaryResponse {
    private UUID id;
    private String title;
    private String venue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EventStatus status;
}
