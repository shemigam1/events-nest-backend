package group.moniepoint.eventsnestserver.events.models;

/**
 * Role held on a specific event via {@link EventMembership}.
 *
 *   ORGANIZER     — created or owns the event. All permissions implicit.
 *   EVENT_MANAGER — invited by the organiser to help operate the event.
 *                   Per-event permissions are stored in
 *                   {@code event_memberships.permissions} (text[]) and
 *                   modelled by ManagerPermission.
 *   ATTENDEE      — booked a ticket.
 *   CHECKIN_STAFF — historically here. Check-in staff actually use
 *                   token-based invites (no User account at all); this
 *                   enum value is kept for backward compatibility with any
 *                   existing rows. New check-in staff don't create
 *                   memberships of this kind.
 */
public enum EventRole {
    ORGANIZER,
    EVENT_MANAGER,
    ATTENDEE,
    CHECKIN_STAFF,
    /** Assigned by an organiser to help manage event logistics (M3). */
    MANAGER,
    /** Service provider engaged per event (M4 — placeholder, not yet fully wired). */
    VENDOR
}
