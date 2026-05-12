package group.moniepoint.eventsnestserver.eventmanagers.model;

public enum ManagerInvitationStatus {
    /** Awaiting the manager's response. */
    PENDING,
    /** Manager accepted — an EVENT_MANAGER EventMembership has been created. */
    ACCEPTED,
    /** Manager declined. The organiser can re-invite by overwriting the row. */
    REJECTED,
    /** Organiser revoked before the manager responded. */
    REVOKED
}
