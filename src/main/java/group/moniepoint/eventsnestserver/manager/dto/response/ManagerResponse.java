package group.moniepoint.eventsnestserver.manager.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Represents one manager assignment on an event. */
@Getter
@Builder
public class ManagerResponse {
    private UUID membershipId;
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private String assignedByUserId;
    private String assignedByName;
    private LocalDateTime assignedAt;
}
