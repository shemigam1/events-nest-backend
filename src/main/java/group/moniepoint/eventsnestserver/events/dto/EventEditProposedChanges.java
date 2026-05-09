package group.moniepoint.eventsnestserver.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEditProposedChanges(
        String description,
        String venue,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime checkInStartTime
) {}
