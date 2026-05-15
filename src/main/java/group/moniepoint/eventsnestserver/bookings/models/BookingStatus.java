package group.moniepoint.eventsnestserver.bookings.models;

public enum BookingStatus {
    /** Booking created; payment not yet received. */
    PENDING_PAYMENT,
    /** Payment confirmed and tickets issued. */
    CONFIRMED,
    CANCELLED
}
