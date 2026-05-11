package group.moniepoint.eventsnestserver.auth.model;

/**
 * Global system role. Per PRD §2.2 — mutually exclusive. A user holds
 * exactly one of these at a time.
 *
 *   USER          — attendee portal (browse, book, review). Default on
 *                   registration.
 *   EVENT_MANAGER — manager portal. Sees every event they're assigned to.
 *                   Cannot book tickets on their own account (would need a
 *                   second USER account for that — see PRD note).
 *   ADMIN         — platform moderation. Cannot hold other roles.
 *
 * VENDOR will join this enum in M4.
 */
public enum Role {
    USER,
    EVENT_MANAGER,
    ADMIN
}
