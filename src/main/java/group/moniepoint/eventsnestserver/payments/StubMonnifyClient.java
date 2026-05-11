package group.moniepoint.eventsnestserver.payments;

import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionRequest;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionResponse;
import group.moniepoint.eventsnestserver.payments.dto.VerifyTransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No-network Monnify stand-in for local dev. Returns a synthetic transaction
 * reference and a checkout URL that points at the local frontend's
 * {@code /stub-payment} page, where the dev can manually mark the booking
 * paid (or just hit the verify endpoint to flip it).
 *
 * verifyTransaction reports PAID for any reference it has seen via
 * initializeTransaction. That way, in dev you can:
 *   1. POST /bookings              — booking goes PENDING, ref stored
 *   2. POST /payments/monnify/verify/{ref} — flips to PAID, issues tickets
 * and you've exercised the whole flow without touching real money.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monnify.enabled", havingValue = "false", matchIfMissing = true)
public class StubMonnifyClient implements MonnifyClient {

    private final ConcurrentHashMap<String, BigDecimal> seenRefs = new ConcurrentHashMap<>();
    private final String frontendBaseUrl;

    public StubMonnifyClient(@Value("${app.frontend-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        log.info("StubMonnifyClient active — set MONNIFY_ENABLED=true to use the real gateway.");
    }

    @Override
    public InitializeTransactionResponse initializeTransaction(InitializeTransactionRequest request) {
        String transactionReference = "STUB-" + UUID.randomUUID();
        seenRefs.put(transactionReference, request.getAmount());
        String checkoutUrl = frontendBaseUrl + "/stub-payment?ref=" + transactionReference
                + "&amount=" + request.getAmount();
        log.info("Stub Monnify init — paymentReference={}, transactionReference={}, amount={}",
                request.getPaymentReference(), transactionReference, request.getAmount());
        return InitializeTransactionResponse.builder()
                .transactionReference(transactionReference)
                .paymentReference(request.getPaymentReference())
                .checkoutUrl(checkoutUrl)
                .build();
    }

    @Override
    public VerifyTransactionResponse verifyTransaction(String transactionReference) {
        BigDecimal amount = seenRefs.get(transactionReference);
        if (amount == null) {
            return VerifyTransactionResponse.builder()
                    .transactionReference(transactionReference)
                    .status(MonnifyPaymentStatus.FAILED)
                    .build();
        }
        return VerifyTransactionResponse.builder()
                .transactionReference(transactionReference)
                .status(MonnifyPaymentStatus.PAID)
                .amountPaid(amount)
                .paidOn(LocalDateTime.now())
                .build();
    }
}
