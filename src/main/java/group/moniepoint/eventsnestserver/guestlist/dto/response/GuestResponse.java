package group.moniepoint.eventsnestserver.guestlist.dto.response;

import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
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
public class GuestResponse {
    private UUID id;
    private UUID eventId;
    private String name;
    private String email;
    private String phone;
    private RsvpStatus rsvpStatus;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
    private String note;
}
