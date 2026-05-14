package group.moniepoint.eventsnestserver.eventmanagers.model;

/**
 * Lifecycle of an event manager application.
 *
 *   PENDING   — submitted, awaiting admin review.
 *   APPROVED  — admin approved. {@code users.role} was flipped to
 *               EVENT_MANAGER and an {@code event_manager_profiles} row
 *               was created.
 *   REJECTED  — admin declined. Can re-apply (the application row gets
 *               overwritten, not duplicated).
 *   SUSPENDED — manager was previously approved but has since been
 *               suspended by an admin (quality/abuse). Existing event
 *               memberships keep working until the organiser revokes them.
 */
public enum ManagerApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}
