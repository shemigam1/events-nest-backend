package group.moniepoint.eventsnestserver.events.models;

public enum EventRole {
    ORGANIZER,
    ATTENDEE,
    CHECKIN_STAFF,
    /** Assigned by an organiser to help manage event logistics (M3). */
    MANAGER,
    /** Service provider engaged per event (M4 — placeholder, not yet fully wired). */
    VENDOR
}
