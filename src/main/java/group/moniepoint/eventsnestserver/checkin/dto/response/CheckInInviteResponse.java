package group.moniepoint.eventsnestserver.checkin.dto.response;

import group.moniepoint.eventsnestserver.checkin.model.CheckInInviteStatus;
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
public class CheckInInviteResponse {
    private UUID id;
    private String name;
    private String email;
    private CheckInInviteStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private String rawToken; // populated only at creation time, null in list responses
}
