package group.moniepoint.eventsnestserver.auth.model;

/**
 * Global system role. Per PRD §2.2 — mutually exclusive. A user holds
 * exactly one of these at a time.
 *
 *   USER  — default. Attendee portal (browse, book, review). USERs become
 *           organisers / managers per-event via {@code EventMembership}.
 *   ADMIN — platform moderation. Cannot hold other roles.
 *
 * VENDOR will join in M4. Per-event manager status is held via
 * {@link group.moniepoint.eventsnestserver.events.models.EventRole#MANAGER}
 * on the membership row — not as a global system role.
 */
public enum Role {
    USER,
    ADMIN
}
