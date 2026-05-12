package group.moniepoint.eventsnestserver.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outputs from {@link group.moniepoint.eventsnestserver.payments.MonnifyClient#initializeTransaction}.
 *
 * {@code checkoutUrl} is what the frontend redirects to. {@code transactionReference}
 * is Monnify's internal id; we store it on the booking for webhook idempotency.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializeTransactionResponse {
    /** Monnify-assigned id; stored on bookings.monnify_transaction_ref. */
    private String transactionReference;
    /** Echo of our paymentReference (booking UUID). */
    private String paymentReference;
    /** URL the frontend redirects to so the user can pay. */
    private String checkoutUrl;
}
