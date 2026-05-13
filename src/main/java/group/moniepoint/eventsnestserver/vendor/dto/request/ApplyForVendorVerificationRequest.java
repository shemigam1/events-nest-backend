package group.moniepoint.eventsnestserver.vendor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplyForVendorVerificationRequest {

    @NotBlank(message = "Service type is required")
    @Size(max = 100)
    private String serviceType;

    @Size(max = 2000)
    private String description;
}
