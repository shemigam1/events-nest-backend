package group.moniepoint.eventsnestserver.checkin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CheckInRequest {

    @NotBlank(message = "staffToken is required")
    private String staffToken;

    @NotBlank(message = "qrCode is required")
    private String qrCode;
}
