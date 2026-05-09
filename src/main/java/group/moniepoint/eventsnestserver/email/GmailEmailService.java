package group.moniepoint.eventsnestserver.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Gmail SMTP implementation.
 * Switch to this by setting mail.provider=gmail in env.properties.
 * Requires GMAIL_USERNAME and GMAIL_APP_PASSWORD to be set.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.provider", havingValue = "gmail")
public class GmailEmailService extends AbstractEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Override
    protected void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent via Gmail SMTP to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email to {} via Gmail: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email via Gmail SMTP", e);
        }
    }
}
