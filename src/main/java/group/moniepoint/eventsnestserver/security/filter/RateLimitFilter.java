package group.moniepoint.eventsnestserver.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Token-bucket rate limiter backed by Redis (via Bucket4j).
 *
 * <p>Two tiers:
 * <ul>
 *   <li><b>Auth endpoints</b> ({@code /api/v1/auth/**}) — 10 requests/minute per IP.
 *       Protects against credential-stuffing and brute-force login attacks.</li>
 *   <li><b>Global API</b> — 200 requests/minute per IP. Prevents a single client
 *       from monopolising the server under load.</li>
 * </ul>
 *
 * <p>Buckets are keyed by {@code tier:clientIp} so limits are isolated per tier.
 * Using Redis as the backing store means the limit is shared across all app
 * instances — horizontal scaling doesn't multiply each client's allowance.
 *
 * <p>Responses include standard rate-limit headers so clients can back off
 * gracefully without hard-coding retry delays.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_RPM      = 10;
    private static final int GLOBAL_RPM    = 200;
    private static final Duration ONE_MIN  = Duration.ofMinutes(1);

    private final ProxyManager<String> proxyManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip   = resolveClientIp(request);
        String path = request.getRequestURI();

        boolean isAuth = path.startsWith("/api/v1/auth/");
        String bucketKey = (isAuth ? "auth:" : "api:") + ip;
        int limit = isAuth ? AUTH_RPM : GLOBAL_RPM;

        Bucket bucket = proxyManager.builder()
                .build(bucketKey, buildConfig(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"success":false,"message":"Too many requests — please slow down","retryAfterSeconds":%d}
                    """.formatted(retryAfterSeconds));
            log.warn("Rate limit exceeded for ip={} path={} tier={}", ip, path, isAuth ? "auth" : "api");
        }
    }

    private static Supplier<BucketConfiguration> buildConfig(int requestsPerMinute) {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, ONE_MIN)
                        .build())
                .build();
    }

    /**
     * Resolves the real client IP, honouring {@code X-Forwarded-For} when the
     * app sits behind a reverse proxy or load balancer. Falls back to the
     * remote address when the header is absent or empty.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated chain; the first entry is the
            // original client. Trust only the leftmost IP.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
