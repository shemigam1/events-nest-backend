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

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private static final String BREVO_API = "https://api.brevo.com/v3";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${brevo.from}")
    private String fromAddress;

    private final RestClient restClient;

    public EmailServiceImpl(@Value("${brevo.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(BREVO_API)
                .defaultHeader("api-key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

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
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", "EventsNest", "email", extractEmail(fromAddress)),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlBody);
        try {
            restClient.post()
                    .uri("/smtp/email")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email sent via Brevo API to {}: {}", toEmail, subject);
        } catch (RestClientResponseException e) {
            log.error("Brevo API rejected email to {} — status {}: {}",
                    toEmail, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send email via Brevo API", e);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email via Brevo API", e);
        }
    }

    private static String extractEmail(String from) {
        if (from == null) return from;
        int lt = from.indexOf('<');
        int gt = from.indexOf('>');
        return (lt >= 0 && gt > lt) ? from.substring(lt + 1, gt).trim() : from.trim();
    }

    // ─── templates ───────────────────────────────────────────────────────────────

    private String buildInvitationEmail(String registrationUrl) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: auto; padding: 24px;">
                  <h2 style="color: #1a1a2e;">You're invited to EventsNest</h2>
                  <p>An admin has invited you to join <strong>EventsNest</strong> as a platform administrator.</p>
                  <p>Click the button below to set up your account.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                       text-decoration: none; border-radius: 6px; font-size: 16px;">
                      Set Up My Admin Account
                    </a>
                  </div>
                  <p style="color: #888; font-size: 13px;">
                    This link expires in <strong>7 days</strong>. If you didn't expect this, ignore it.
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
                  <p>Use the token below to authenticate at the check-in terminal:</p>
                  <div style="background: #f4f4f4; border-left: 4px solid #3b4cca; padding: 16px; margin: 24px 0;
                              font-family: monospace; font-size: 16px; word-break: break-all;">%s</div>
                  <p style="color: #e53e3e; font-size: 13px;">Keep this token private — it grants check-in access to the event.</p>
                  <p style="color: #888; font-size: 13px;">Expires 24 hours after the event ends.</p>
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
                  <p>Your booking for <strong>%s</strong> is confirmed.</p>
                  <table cellpadding="0" cellspacing="0" border="0" style="margin: 24px 0; width: 100%%; border: 1px solid #eee; border-radius: 8px;">
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
                       text-decoration: none; border-radius: 6px; font-size: 16px;">View my tickets</a>
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
                  <p><strong>%s</strong> has been approved and published.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                       text-decoration: none; border-radius: 6px; font-size: 16px;">Open organiser console</a>
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
                  <p><strong>%s</strong> was returned to draft with the following note:</p>
                  <div style="background: #fff7e6; border-left: 4px solid #B8770A; padding: 16px; margin: 24px 0; font-size: 14px;">%s</div>
                  <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background-color: #3b4cca; color: white; padding: 14px 28px;
                       text-decoration: none; border-radius: 6px; font-size: 16px;">Edit event</a>
                  </div>
                  <hr style="border: none; border-top: 1px solid #eee; margin-top: 32px;" />
                  <p style="color: #aaa; font-size: 12px;">EventsNest Platform</p>
                </body>
                </html>
                """.formatted(safeName, eventTitle, safeReason, editUrl);
    }
}
