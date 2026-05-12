package group.moniepoint.eventsnestserver.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads HTML email templates from {@code classpath:templates/email/} and
 * resolves {@code {{placeholder}}} variables from a supplied map.
 *
 * <p>Templates are read fresh on every call — no caching — so changes to
 * the HTML files are picked up on the next app restart without a code change.
 *
 * <p>Usage:
 * <pre>{@code
 * String html = templateLoader.render("booking-confirmation.html", Map.of(
 *     "attendeeName", "Jane Doe",
 *     "eventTitle",   "Lagos Jazz Night"
 * ));
 * }</pre>
 */
@Component
@Slf4j
public class EmailTemplateLoader {

    private static final String TEMPLATE_DIR = "templates/email/";

    /**
     * Renders a named template with the given variable bindings.
     *
     * @param templateName filename relative to {@code templates/email/}, e.g. {@code "booking-confirmation.html"}
     * @param variables    map of {@code {{key}}} → replacement value; null values render as empty string
     * @return the rendered HTML string
     * @throws IllegalStateException if the template file cannot be read
     */
    public String render(String templateName, Map<String, String> variables) {
        String template = load(templateName);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            template = template.replace("{{" + entry.getKey() + "}}", value);
        }
        return template;
    }

    private String load(String templateName) {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + templateName);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load email template '{}': {}", templateName, e.getMessage());
            throw new IllegalStateException("Email template not found: " + templateName, e);
        }
    }
}
