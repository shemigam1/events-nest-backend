package group.moniepoint.eventsnestserver.events.dto.response;

import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.EventVisibility;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryResponse {
    private UUID id;
    private String code;
    private String title;
    private String venue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EventStatus status;
    private EventVisibility visibility;
    private String coverImageUrl;
    private List<TicketTierResponse> tiers;
    /** Canonical public URL for sharing — based on the event short code. */
    private String publicUrl;
}
