package group.moniepoint.eventsnestserver.guestlist.dto.request;

import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateGuestStatusRequest {

    @NotNull(message = "rsvpStatus is required")
    private RsvpStatus rsvpStatus;
}
