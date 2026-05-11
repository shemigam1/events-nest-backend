package group.moniepoint.eventsnestserver.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Inputs for {@link group.moniepoint.eventsnestserver.payments.MonnifyClient#initializeTransaction}.
 * The {@code paymentReference} field MUST be unique per booking — we use the
 * booking UUID so a retry doesn't double-process.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializeTransactionRequest {
    private BigDecimal amount;
    /** ISO 4217 — for EventsNest this is always NGN. */
    private String currencyCode;
    /** Our internal reference. Booking UUID. */
    private String paymentReference;
    private String customerName;
    private String customerEmail;
    private String paymentDescription;
    /** Where Monnify redirects the user after checkout. */
    private String redirectUrl;
}
