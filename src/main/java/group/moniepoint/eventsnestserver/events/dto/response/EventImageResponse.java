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
public class EventImageResponse {
    private UUID eventId;
    private String coverImageUrl;
}
