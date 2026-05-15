package group.moniepoint.eventsnestserver.bookings.models;

public enum PaymentStatus {
    /** Booking created, awaiting payment confirmation. Tickets are NOT issued yet. */
    PENDING,

    /** Payment confirmed via gateway callback or /verify. Tickets issued. */
    PAID,

    /** Payment gateway reported failure (timeout, declined, cancelled). Tier capacity restored. */
    FAILED,

    /** Booking was cancelled after PAID — handled by the cancel-booking flow. */
    REFUNDED
}
