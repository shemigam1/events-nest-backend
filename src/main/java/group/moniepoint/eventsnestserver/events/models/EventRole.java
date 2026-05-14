package group.moniepoint.eventsnestserver.events.models;

/**
 * Role held on a specific event via {@link EventMembership}.
 *
 *   ORGANIZER     — created or owns the event. All permissions implicit.
 *   MANAGER       — assigned by the organiser (see ManagerService) to help
 *                   run the event. Per-event permissions stored in
 *                   {@code event_memberships.permissions} (text[]) and
 *                   modelled by ManagerPermission.
 *   ATTENDEE      — booked a ticket.
 *   CHECKIN_STAFF — legacy. New check-in staff use the token-based invite
 *                   flow (no User account, no membership). The value is
 *                   retained for any existing rows.
 *   VENDOR        — service provider engaged per event. Placeholder for M4.
 */
public enum EventRole {
    ORGANIZER,
    MANAGER,
    ATTENDEE,
    CHECKIN_STAFF,
    VENDOR
}
