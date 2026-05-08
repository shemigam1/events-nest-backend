package group.moniepoint.eventsnestserver.email;

import java.math.BigDecimal;

public interface EmailService {

    void sendAdminInvitation(String toEmail, String token);

    void sendCheckInStaffInvite(String toEmail, String staffName, String rawToken, String eventTitle);

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
