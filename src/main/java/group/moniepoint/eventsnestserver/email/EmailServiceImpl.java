package group.moniepoint.eventsnestserver.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional email via the Resend HTTP API
 * (https://resend.com/docs/api-reference/emails/send-email).
 *
 * Replaces the previous SMTP/JavaMailSender implementation. The interface
 * is unchanged so callers (AdminServiceImpl, EmailJobPoller) stay the same.
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private static final String RESEND_API = "https://api.resend.com";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${resend.from}")
    private String fromAddress;

    private final RestClient restClient;

    public EmailServiceImpl(@Value("${resend.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(RESEND_API)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

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

    private void send(String toEmail, String subject, String htmlBody) {
        Map<String, Object> payload = Map.of(
                "from", fromAddress,
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlBody);
        try {
            restClient.post()
                    .uri("/emails")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Resend returns structured error JSON; surface status + body for triage.
            log.error("Resend rejected email to {} with status {}: {}",
                    toEmail, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send email via Resend", e);
        } catch (Exception e) {
            log.error("Network failure sending email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email via Resend", e);
        }
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
                  <p style="color: #888; font-size: 13px;">
                    This token expires 24 hours after the event ends.
                  </p>
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
        String formattedTotal = totalAmount == null
                ? "Free"
                : "₦" + totalAmount.toPlainString();
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
                      <td style="padding: 12px 16px; font-size: 13px; color: #1a1a2e; font-weight: 600;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; border-top: 1px solid #eee;">Quantity</td>
                      <td style="padding: 12px 16px; font-size: 13px; color: #1a1a2e; font-weight: 600; border-top: 1px solid #eee;">%d ticket(s)</td>
                    </tr>
                    <tr style="background: #f8f9fc;">
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; border-top: 1px solid #eee;">Total</td>
                      <td style="padding: 12px 16px; font-size: 13px; color: #1a1a2e; font-weight: 600; border-top: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 12px 16px; font-size: 13px; color: #555; border-top: 1px solid #eee;">Reference</td>
                      <td style="padding: 12px 16px; font-size: 12px; color: #1a1a2e; font-family: monospace; border-top: 1px solid #eee;">%s</td>
                    </tr>
                  </table>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px; text-decoration: none; border-radius: 6px; font-size: 16px;">
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
                  <h2 style="color: #0F9D58;">Your event is live 🎉</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p><strong>%s</strong> has been reviewed and published. Attendees can discover and book tickets right away.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px; text-decoration: none; border-radius: 6px; font-size: 16px;">
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
                  <div style="background: #fff7e6; border-left: 4px solid #B8770A; padding: 16px; margin: 24px 0; font-size: 14px; color: #1a1a2e;">
                    %s
                  </div>
                  <p>Edit the event and resubmit when you’re ready — it’ll go back into the approval queue.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px; text-decoration: none; border-radius: 6px; font-size: 16px;">
                      Edit event
                    </a>
                  </div>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(safeName, eventTitle, safeReason, editUrl);
    }

    private String buildInvitationEmail(String registrationUrl) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #1a1a2e;">You're invited to EventsNest</h2>
                  <p>An admin has invited you to join <strong>EventsNest</strong> as a platform administrator.</p>
                  <p>Click the button below to set up your account. You'll be asked to provide your name and create a password.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s"
                       style="background-color: #3b4cca; color: white; padding: 14px 28px;
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
}
