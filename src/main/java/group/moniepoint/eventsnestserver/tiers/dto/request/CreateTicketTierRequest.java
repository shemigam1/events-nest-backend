package group.moniepoint.eventsnestserver.tiers.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateTicketTierRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0.00", message = "price must be zero or greater")
    private BigDecimal price;

    @NotBlank
    @Size(max = 10)
    private String rowPrefix;

    @NotNull
    @Min(value = 1, message = "rowCount must be at least 1")
    private Integer rowCount;

    @NotNull
    @Min(value = 1, message = "seatsPerRow must be at least 1")
    private Integer seatsPerRow;

    /**
     * Optional day scope. Null = whole-event tier (valid for every day).
     * Non-null = day-specific tier; must reference a day of the same event.
     * Only honoured by the standalone tier-creation endpoint; ignored when
     * tiers are created inline during event creation (no days exist yet).
     */
    private UUID eventDayId;
}
