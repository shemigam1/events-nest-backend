package group.moniepoint.eventsnestserver.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
