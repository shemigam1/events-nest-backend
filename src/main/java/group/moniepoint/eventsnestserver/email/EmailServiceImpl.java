package group.moniepoint.eventsnestserver.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ─── public interface ─────────────────────────────────────────────────────────

    @Override
    public void sendAdminInvitation(String toEmail, String token) {
        String registrationUrl = frontendUrl + "/admin/register?token=" + token;
        send(toEmail,
                "You've been invited to join EventsNest as an Admin",
                buildInvitationEmail(registrationUrl));
    }

    @Override
    public void sendCheckInStaffInvite(String toEmail, String staffName, String rawToken, String eventTitle) {
        send(toEmail,
                "You've been invited as check-in staff for " + eventTitle,
                buildCheckInStaffEmail(staffName, rawToken, eventTitle));
    }

    @Override
    public void sendBookingConfirmation(String toEmail,
                                        String attendeeName,
                                        String eventTitle,
                                        String tierName,
                                        Integer quantity,
                                        BigDecimal totalAmount,
                                        String paymentReference) {
        send(toEmail,
                "Your booking for " + eventTitle + " is confirmed",
                buildBookingConfirmationEmail(attendeeName, eventTitle, tierName, quantity, totalAmount, paymentReference));
    }

    @Override
    public void sendEventApproved(String toEmail, String organiserName, String eventTitle) {
        send(toEmail,
                "Your event \"" + eventTitle + "\" has been approved",
                buildEventApprovedEmail(organiserName, eventTitle));
    }

    @Override
    public void sendEventRejected(String toEmail, String organiserName, String eventTitle, String reason) {
        send(toEmail,
                "Your event \"" + eventTitle + "\" needs revision",
                buildEventRejectedEmail(organiserName, eventTitle, reason));
    }

    // ─── core sender ─────────────────────────────────────────────────────────────

    private void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", toEmail, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email via Brevo", e);
        }
    }

    // ─── templates ───────────────────────────────────────────────────────────────

    private String buildInvitationEmail(String registrationUrl) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #1a1a2e;">You're invited to EventsNest</h2>
                  <p>An admin has invited you to join <strong>EventsNest</strong> as a platform administrator.</p>
                  <p>Click the button below to set up your account. You'll be asked to provide your name and create a password.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                              text-decoration: none; border-radius: 6px; font-size: 16px;">
                      Set Up My Admin Account
                    </a>
                  </div>
                  <p style="color: #888; font-size: 13px;">
                    This link will expire in <strong>7 days</strong>. If you did not expect this invitation, you can safely ignore this email.
                  </p>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(registrationUrl);
    }

    private String buildCheckInStaffEmail(String staffName, String rawToken, String eventTitle) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #1a1a2e;">Check-In Staff Invitation</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>You have been invited to serve as check-in staff for <strong>%s</strong>.</p>
                  <p>Use the token below to authenticate at the check-in terminal on the day of the event:</p>
                  <div style="background: #f4f4f4; border-left: 4px solid #3b4cca; padding: 16px; margin: 24px 0;
                              font-family: monospace; font-size: 16px; word-break: break-all;">
                    %s
                  </div>
                  <p style="color: #e53e3e; font-size: 13px;">
                    Keep this token private. Do not share it — it grants check-in access to the event.
                  </p>
                  <p style="color: #888; font-size: 13px;">This token expires 24 hours after the event ends.</p>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(staffName, eventTitle, rawToken);
    }

    private String buildBookingConfirmationEmail(String attendeeName,
                                                  String eventTitle,
                                                  String tierName,
                                                  Integer quantity,
                                                  BigDecimal totalAmount,
                                                  String paymentReference) {
        String safeName = attendeeName == null || attendeeName.isBlank() ? "there" : attendeeName;
        String formattedTotal = totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0
                ? "Free" : "₦" + totalAmount.toPlainString();
        String reference = paymentReference == null ? "—" : paymentReference;
        String myTicketsUrl = frontendUrl + "/tickets";
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #1a1a2e;">Booking confirmed</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Your booking for <strong>%s</strong> is confirmed. Show your QR code at the gate to be checked in.</p>
                  <table cellpadding="0" cellspacing="0" border="0" style="margin: 24px 0; width: 100%%; border: 1px solid #eee; border-radius: 8px; overflow: hidden;">
                    <tr style="background: #f8f9fc;">
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; width: 40%%;">Tier</td>
                      <td style="padding: 12px 16px; font-size: 13px; font-weight: 600;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; border-top: 1px solid #eee;">Quantity</td>
                      <td style="padding: 12px 16px; font-size: 13px; font-weight: 600; border-top: 1px solid #eee;">%d ticket(s)</td>
                    </tr>
                    <tr style="background: #f8f9fc;">
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; border-top: 1px solid #eee;">Total</td>
                      <td style="padding: 12px 16px; font-size: 13px; font-weight: 600; border-top: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; border-top: 1px solid #eee;">Reference</td>
                      <td style="padding: 12px 16px; font-size: 12px; font-family: monospace; border-top: 1px solid #eee;">%s</td>
                    </tr>
                  </table>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                       text-decoration: none; border-radius: 6px; font-size: 16px;">
                      View my tickets
                    </a>
                  </div>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(safeName, eventTitle, tierName, quantity, formattedTotal, reference, myTicketsUrl);
    }

    private String buildEventApprovedEmail(String organiserName, String eventTitle) {
        String safeName = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        String dashboardUrl = frontendUrl + "/organiser";
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #0F9D58;">Your event is live!</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p><strong>%s</strong> has been reviewed and published. Attendees can discover and book tickets right away.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                       text-decoration: none; border-radius: 6px; font-size: 16px;">
                      Open organiser console
                    </a>
                  </div>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(safeName, eventTitle, dashboardUrl);
    }

    private String buildEventRejectedEmail(String organiserName, String eventTitle, String reason) {
        String safeName = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        String safeReason = reason == null || reason.isBlank() ? "(no reason provided)" : reason;
        String editUrl = frontendUrl + "/organiser";
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #B8770A;">Your event needs revision</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>An admin reviewed <strong>%s</strong> and returned it to draft so you can make changes:</p>
                  <div style="background: #fff7e6; border-left: 4px solid #B8770A; padding: 16px; margin: 24px 0; font-size: 14px;">
                    %s
                  </div>
                  <p>Edit the event and resubmit when you're ready.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                       text-decoration: none; border-radius: 6px; font-size: 16px;">
                      Edit event
                    </a>
                  </div>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(safeName, eventTitle, safeReason, editUrl);
    }
}
