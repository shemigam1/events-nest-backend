package group.moniepoint.eventsnestserver.events.dto.response;

import group.moniepoint.eventsnestserver.events.dto.EventEditProposedChanges;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingUpdateResponse(
        UUID id,
        EventEditProposedChanges proposedChanges,
        EventEditStatus status,
        String rejectionReason,
        LocalDateTime submittedAt
) {}
