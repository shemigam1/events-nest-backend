package group.moniepoint.eventsnestserver.bookings.models;

public enum PaymentStatus {
    /**
     * Booking created, awaiting payment confirmation from Monnify.
     * Tickets are NOT issued in this state.
     */
    PENDING,

    /** Payment confirmed via webhook or /verify. Tickets issued. */
    PAID,

    /**
     * Monnify reported the transaction failed (timeout, declined, cancelled).
     * Tier capacity has been restored to the pool.
     */
    FAILED,

    /** Booking was cancelled after PAID — handled by the cancel-booking flow. */
    REFUNDED
}
