package group.moniepoint.eventsnestserver.budget.dto.request;

import group.moniepoint.eventsnestserver.budget.model.BudgetCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AddLineItemRequest {

    @NotNull(message = "category is required")
    private BudgetCategory category;

    @NotBlank(message = "description is required")
    @Size(max = 500)
    private String description;

    @NotNull(message = "plannedAmount is required")
    @DecimalMin(value = "0.01", message = "plannedAmount must be positive")
    private BigDecimal plannedAmount;
}
