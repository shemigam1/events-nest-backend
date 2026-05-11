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

    // Primary: scanned from QR code. Provide either qrCode or shortCode — not both.
    private String qrCode;

    // Fallback: 8-char code printed on the ticket for manual entry when QR cannot be scanned.
    private String shortCode;
}
