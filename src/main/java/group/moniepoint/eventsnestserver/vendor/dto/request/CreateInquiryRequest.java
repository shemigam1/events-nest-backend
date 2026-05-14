package group.moniepoint.eventsnestserver.vendor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInquiryRequest {

    @NotBlank
    private String vendorId;

    @NotBlank
    private String message;

    private String serviceType;
}
