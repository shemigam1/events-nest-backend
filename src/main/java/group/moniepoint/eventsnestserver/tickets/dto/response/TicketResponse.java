package group.moniepoint.eventsnestserver.tickets.dto.response;

import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
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
public class TicketResponse {
    private UUID id;
    private UUID bookingId;
    private UUID tierId;
    private String tierName;
    private UUID eventId;
    private String eventTitle;
    private String seatNumber;
    private String qrCode;
    private TicketStatus status;
    private LocalDateTime checkedInAt;
    private String checkedInByLabel;
    private LocalDateTime issuedAt;
}
