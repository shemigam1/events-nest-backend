package group.moniepoint.eventsnestserver.budget.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MarkLineItemPaidRequest {

    @NotNull(message = "actualAmount is required")
    @DecimalMin(value = "0.01", message = "actualAmount must be positive")
    private BigDecimal actualAmount;
}
