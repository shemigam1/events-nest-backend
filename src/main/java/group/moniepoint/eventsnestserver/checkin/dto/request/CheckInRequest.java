package group.moniepoint.eventsnestserver.checkin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

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

    /**
     * Which calendar day of the event is being checked in for. Required when the event has
     * multiple days and the ticket tier is valid for the whole event (not day-scoped); inferred
     * for single-day events and for day-scoped tiers.
     */
    private UUID eventDayId;
}
