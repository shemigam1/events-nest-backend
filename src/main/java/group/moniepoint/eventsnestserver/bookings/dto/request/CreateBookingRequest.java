package group.moniepoint.eventsnestserver.bookings.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateBookingRequest {

    @NotNull
    private UUID tierId;

    @NotNull
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 20, message = "quantity must not exceed 20 per booking")
    private Integer quantity;
}
