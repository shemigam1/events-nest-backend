package group.moniepoint.eventsnestserver.payments;

/**
 * Statuses Monnify returns on its API + webhooks. Mapped one-to-one onto
 * {@link group.moniepoint.eventsnestserver.bookings.models.PaymentStatus}
 * by the booking service.
 *
 * Monnify documents richer values (OVERPAID, PARTIALLY_PAID, etc.); we
 * collapse all non-PAID success-adjacent states to PAID for simplicity —
 * if Monnify says the money is in the account, we issue the tickets.
 */
public enum MonnifyPaymentStatus {
    PENDING,
    PAID,
    FAILED
}
