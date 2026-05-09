package group.moniepoint.eventsnestserver.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Brevo HTTP API implementation — active by default.
 * Switch to Gmail by setting mail.provider=gmail in env.properties.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "mail.provider", havingValue = "brevo", matchIfMissing = true)
public class EmailServiceImpl extends AbstractEmailService {

    private static final String BREVO_API = "https://api.brevo.com/v3";

    @Value("${brevo.from:aabc33001@smtp-brevo.com}")
    private String fromAddress;

    private final RestClient restClient;

    public EmailServiceImpl(@Value("${brevo.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(BREVO_API)
                .defaultHeader("api-key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    protected void send(String toEmail, String subject, String htmlBody) {
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
            log.info("Email sent via Brevo API to {}", toEmail);
        } catch (RestClientResponseException e) {
            log.error("Brevo API rejected email to {} — status {}: {}",
                    toEmail, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send email via Brevo API", e);
        } catch (Exception e) {
            log.error("Failed to send email to {} via Brevo: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email via Brevo API", e);
        }
    }

    private static String extractEmail(String from) {
        if (from == null) return from;
        int lt = from.indexOf('<');
        int gt = from.indexOf('>');
        return (lt >= 0 && gt > lt) ? from.substring(lt + 1, gt).trim() : from.trim();
    }
}
