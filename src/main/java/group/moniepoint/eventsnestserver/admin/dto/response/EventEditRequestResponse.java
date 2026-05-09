package group.moniepoint.eventsnestserver.admin.dto.response;

import group.moniepoint.eventsnestserver.events.dto.EventEditProposedChanges;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;
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
public class EventEditRequestResponse {
    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private EventEditProposedChanges proposedChanges;
    private EventEditStatus status;
    private String rejectionReason;
    private String submittedById;
    private String submittedByName;
    private String submittedByEmail;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
