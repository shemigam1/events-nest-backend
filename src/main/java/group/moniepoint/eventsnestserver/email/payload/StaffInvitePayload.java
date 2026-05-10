package group.moniepoint.eventsnestserver.email.payload;

import java.util.UUID;

/**
 * Stored as JSON in {@code email_jobs.payload_json} when the job's type is
 * {@code STAFF_INVITE}. The poller deserialises it and hands the fields to
 * {@code EmailService.sendCheckInStaffInvite}.
 *
 * Replaces the legacy {@code staff_name} / {@code raw_token} /
 * {@code event_title} columns that the original staff-invite path wrote
 * directly. Those columns remain readable for backward compatibility but
 * new rows go through this payload.
 */
public record StaffInvitePayload(
        String name,
        String rawToken,
        UUID eventId,
        String eventCode,
        String eventTitle
) {}
