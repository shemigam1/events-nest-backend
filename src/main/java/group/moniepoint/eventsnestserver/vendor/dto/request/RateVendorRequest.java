package group.moniepoint.eventsnestserver.vendor.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RateVendorRequest {

    @NotNull(message = "score is required")
    @Min(value = 1, message = "score must be at least 1")
    @Max(value = 5, message = "score must be at most 5")
    private Integer score;

    private String comment;
}
