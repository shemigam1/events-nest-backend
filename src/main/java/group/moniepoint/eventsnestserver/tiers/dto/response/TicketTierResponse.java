package group.moniepoint.eventsnestserver.tiers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTierResponse {
    private UUID id;
    private UUID eventId;
    /** Null means whole-event tier; non-null restricts the tier to that day. */
    private UUID eventDayId;
    private String name;
    private BigDecimal price;
    private String rowPrefix;
    private Integer rowCount;
    private Integer seatsPerRow;
    private Integer totalCapacity;
    private Integer availableCapacity;
    private LocalDateTime createdAt;
}
