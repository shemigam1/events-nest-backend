package group.moniepoint.eventsnestserver.checkin.dto.response;

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
public class CheckInResponse {
    private UUID ticketId;
    private String seatNumber;
    private String tierName;
    private UUID eventId;
    private String eventTitle;
    private String attendeeFirstName;
    private String attendeeLastName;
    private LocalDateTime checkedInAt;
    private String checkedInByLabel;

    /** Calendar day this check-in was recorded against. */
    private UUID eventDayId;
}
