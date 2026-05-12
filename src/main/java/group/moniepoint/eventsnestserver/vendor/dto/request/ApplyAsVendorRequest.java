package group.moniepoint.eventsnestserver.vendor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplyAsVendorRequest {

    @NotBlank(message = "Service type is required")
    private String serviceType;

    private String description;

    private BigDecimal proposedAmount;
}
