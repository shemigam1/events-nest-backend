package group.moniepoint.eventsnestserver.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps a per-request correlation ID into the SLF4J MDC so every log line
 * emitted while handling that request carries it. Enables us to grep one ID
 * and see the entire trace — HTTP entry, service-layer logs, JPA, Kafka
 * publish, downstream — for a single user action.
 *
 * Honours an inbound {@code X-Correlation-Id} header if the client supplies
 * one (useful for orchestrated end-to-end tests or browser-side log tools);
 * otherwise generates a fresh UUID. Echoes the chosen ID back as a response
 * header so the caller can pin a request to a server-side trace.
 *
 * Highest precedence so the ID is set before any auth / business filter runs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Defensive cap so a malicious client can't shove giant strings into MDC / log files. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank() || id.length() > MAX_LENGTH) {
            id = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, id);
            response.setHeader(HEADER, id);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
