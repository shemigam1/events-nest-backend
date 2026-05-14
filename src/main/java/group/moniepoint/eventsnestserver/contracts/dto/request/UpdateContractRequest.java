package group.moniepoint.eventsnestserver.contracts.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateContractRequest {
    private String title;
    private String description;
    private String terms;

    @DecimalMin("1.00")
    private BigDecimal amount;
}
