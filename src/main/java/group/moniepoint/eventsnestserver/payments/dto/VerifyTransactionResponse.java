package group.moniepoint.eventsnestserver.payments.dto;

import group.moniepoint.eventsnestserver.payments.MonnifyPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outputs from {@link group.moniepoint.eventsnestserver.payments.MonnifyClient#verifyTransaction}.
 * Also used internally to model the webhook payload after parsing — same shape.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTransactionResponse {
    private String transactionReference;
    private String paymentReference;
    private MonnifyPaymentStatus status;
    private BigDecimal amountPaid;
    /** When Monnify says the money landed. Null if status != PAID. */
    private LocalDateTime paidOn;
}
