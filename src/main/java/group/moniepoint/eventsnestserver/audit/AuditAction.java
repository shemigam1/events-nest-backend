package group.moniepoint.eventsnestserver.audit;

public enum AuditAction {

    // ── Event lifecycle (admin) ───────────────────────────────────────────────
    APPROVE_EVENT,
    REJECT_EVENT,
    CANCEL_EVENT,
    APPROVE_EVENT_UPDATE,
    REJECT_EVENT_UPDATE,

    // ── User management (admin) ──────────────────────────────────────────────
    ENABLE_USER,
    DISABLE_USER,

    // ── Vendor verification (admin) ──────────────────────────────────────────
    APPROVE_VENDOR_VERIFICATION,
    REJECT_VENDOR_VERIFICATION,

    // ── Bookings (attendee) ───────────────────────────────────────────────────
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,

    // ── Contracts (vendor / organiser) ────────────────────────────────────────
    CONTRACT_SIGNED,

    // ── Security (unauthenticated) ────────────────────────────────────────────
    LOGIN_FAILURE
}
