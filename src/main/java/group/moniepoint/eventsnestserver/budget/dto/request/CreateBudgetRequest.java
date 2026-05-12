package group.moniepoint.eventsnestserver.budget.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateBudgetRequest {

    @NotNull(message = "totalBudget is required")
    @DecimalMin(value = "1.00", message = "budget must be at least ₦1")
    private BigDecimal totalBudget;

    private String notes;
}
