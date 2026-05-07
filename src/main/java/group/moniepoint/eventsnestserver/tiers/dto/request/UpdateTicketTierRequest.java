package group.moniepoint.eventsnestserver.tiers.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Per PRD §3.3: only name and price may be updated. Editing seat layout
 * (rowCount / seatsPerRow) is intentionally not supported here — it must
 * stay locked once tickets have been issued.
 */
@Getter
@Setter
public class UpdateTicketTierRequest {

    @Size(max = 100)
    private String name;

    @DecimalMin(value = "0.00", message = "price must be zero or greater")
    private BigDecimal price;
}
