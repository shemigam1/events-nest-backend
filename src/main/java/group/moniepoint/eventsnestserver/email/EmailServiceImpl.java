package group.moniepoint.eventsnestserver.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendAdminInvitation(String toEmail, String token) {
        String registrationUrl = frontendUrl + "/admin/register?token=" + token;
        String subject = "You've been invited to join EventsNest as an Admin";
        String body = buildInvitationEmail(registrationUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send invitation email", e);
        }
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
