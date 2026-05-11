package group.moniepoint.eventsnestserver.payments;

import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionRequest;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionResponse;
import group.moniepoint.eventsnestserver.payments.dto.VerifyTransactionResponse;

/**
 * Abstraction over the Monnify HTTP API. Two implementations:
 *   * {@link StubMonnifyClient}  — default; logs and returns fake URLs.
 *     Lets local dev exercise the booking flow without real credentials.
 *   * {@link HttpMonnifyClient}  — active when {@code monnify.enabled=true};
 *     calls Monnify's real sandbox / production endpoints.
 *
 * Replace the {@code @ConditionalOnProperty} switch with the real impl in
 * staging/production by setting {@code MONNIFY_ENABLED=true} + supplying
 * api-key, secret-key, and contract-code.
 */
public interface MonnifyClient {

    /**
     * Ask Monnify to create a transaction; returns a hosted checkout URL the
     * frontend can redirect the user to.
     */
    InitializeTransactionResponse initializeTransaction(InitializeTransactionRequest request);

    /**
     * Fetch the current status of a transaction. Used as a fallback to the
     * webhook in case the webhook is delayed or dropped (PRD §5.2).
     */
    VerifyTransactionResponse verifyTransaction(String transactionReference);
}
