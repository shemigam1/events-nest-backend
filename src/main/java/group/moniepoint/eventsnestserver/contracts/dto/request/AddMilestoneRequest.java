package group.moniepoint.eventsnestserver.contracts.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddMilestoneRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    @DecimalMin("1.00")
    private BigDecimal amount;

    private int displayOrder;
}
