package group.moniepoint.eventsnestserver.contracts.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateContractRequest {

    @NotBlank
    private String title;

    private String description;

    private String terms;

    @NotNull
    @DecimalMin("1.00")
    private BigDecimal amount;

    /** The vendor's user ID. */
    @NotBlank
    private String vendorId;

    /** Optional — link to an existing accepted vendor application. */
    private UUID vendorApplicationId;
}
