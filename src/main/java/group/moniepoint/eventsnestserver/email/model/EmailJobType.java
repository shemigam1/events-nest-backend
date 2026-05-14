package group.moniepoint.eventsnestserver.email.model;

/**
 * Discriminator for {@link EmailJob}. New types use the {@code payloadJson}
 * column; the legacy STAFF_INVITE type still uses the dedicated
 * {@code staff_name} / {@code raw_token} / {@code event_title} columns
 * for backward compatibility.
 */
public enum EmailJobType {
    STAFF_INVITE,
    BOOKING_CONFIRMED,
    EVENT_APPROVED,
    EVENT_REJECTED,
    GUEST_RSVP_INVITE,
    RATING_REQUEST,
    BUDGET_ALERT,
    PASSWORD_RESET,
    VENDOR_INQUIRY,
    VENDOR_CONTRACT_OFFER
}
