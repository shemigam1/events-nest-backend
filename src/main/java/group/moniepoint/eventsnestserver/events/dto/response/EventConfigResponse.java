package group.moniepoint.eventsnestserver.events.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventConfigResponse {
    private UUID eventId;
    private boolean ticketingEnabled;
    private boolean guestListEnabled;
    private boolean programmeEnabled;
    private boolean ratingsEnabled;
}
