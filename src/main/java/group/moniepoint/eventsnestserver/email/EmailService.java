package group.moniepoint.eventsnestserver.email;

import java.math.BigDecimal;

public interface EmailService {

    void sendAdminInvitation(String toEmail, String token);

    /**
     * Sent to a check-in staff member when an organiser invites them.
     * Email body contains both the raw token and a deep link
     * {@code <frontendUrl>/checkin?eventId=…&token=…} so the staff can
     * land on the right scanner with credentials pre-filled.
     *
     * @param eventId UUID of the event the staff is being invited to;
     *                may be {@code null} for legacy rows persisted before
     *                the deep-link change — the template degrades to
     *                token-only rendering in that case.
     */
    void sendCheckInStaffInvite(String toEmail,
                                String staffName,
                                String rawToken,
                                String eventTitle,
                                java.util.UUID eventId,
                                String eventCode);

    /**
     * Sent to attendees when their booking is confirmed.
     * Triggered by the BookingConfirmedEvent kafka consumer (via the email
     * outbox for retry safety).
     */
    void sendBookingConfirmation(String toEmail,
                                 String attendeeName,
                                 String eventTitle,
                                 String tierName,
                                 Integer quantity,
                                 BigDecimal totalAmount,
                                 String paymentReference);

    /** Sent to event organisers when an admin approves their submission. */
    void sendEventApproved(String toEmail, String organiserName, String eventTitle);

    /** Sent to event organisers when an admin rejects their submission, with the reason. */
    void sendEventRejected(String toEmail, String organiserName, String eventTitle, String reason);
}
