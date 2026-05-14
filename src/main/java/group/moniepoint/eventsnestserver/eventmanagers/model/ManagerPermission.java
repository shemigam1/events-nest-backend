package group.moniepoint.eventsnestserver.eventmanagers.model;

/**
 * Per-event permissions for an EVENT_MANAGER membership. Stored as a
 * Postgres text[] on {@code event_memberships.permissions}.
 *
 * Permissions are additive. A manager with {@code [GUEST_LIST, PROGRAMME]}
 * can operate those modules on the assigned event but not, say, the budget.
 *
 * Some permissions don't yet have corresponding modules implemented (VENDORS,
 * CONTRACTS, BUDGET) — they're declared here so the organiser-facing invite
 * form can list them and the data model is forward-compatible.
 */
public enum ManagerPermission {
    /** Edit event details (title, description, dates, cover image). */
    EVENT_DETAILS,
    /** Add / remove / update RSVP for guests on the guest list. */
    GUEST_LIST,
    /** CRUD on programme items / run-of-show. */
    PROGRAMME,
    /** Send / revoke check-in staff invites and view the live check-in dashboard. */
    CHECK_IN_STAFF,
    /** View analytics dashboard for the event. */
    ANALYTICS,
    /** Future (M4). Initiate and manage vendor inquiries. */
    VENDORS,
    /** Future (M4). Draft, send, and accept contracts on the organiser's behalf. */
    CONTRACTS,
    /** Future (M5). View and update the budget tracker. */
    BUDGET
}
