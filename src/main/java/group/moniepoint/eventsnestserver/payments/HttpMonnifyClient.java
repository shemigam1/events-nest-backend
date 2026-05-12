package group.moniepoint.eventsnestserver.payments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionRequest;
import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionResponse;
import group.moniepoint.eventsnestserver.payments.dto.VerifyTransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls Monnify's REST API. Activated by {@code monnify.enabled=true}.
 *
 * Auth flow: POST /api/v1/auth/login with HTTP Basic of "apiKey:secretKey"
 * → returns a Bearer access-token used on subsequent calls. Token is cached
 * in memory until it 401s, then refreshed lazily.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monnify.enabled", havingValue = "true")
public class HttpMonnifyClient implements MonnifyClient {

    private final String baseUrl;
    private final String apiKey;
    private final String secretKey;
    private final String contractCode;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String accessToken;

    public HttpMonnifyClient(
            @Value("${monnify.base-url}") String baseUrl,
            @Value("${monnify.api-key}") String apiKey,
            @Value("${monnify.secret-key}") String secretKey,
            @Value("${monnify.contract-code}") String contractCode) {
        this.baseUrl = trimSlash(baseUrl);
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.contractCode = contractCode;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @PostConstruct
    void announce() {
        log.info("HttpMonnifyClient active — baseUrl={}, contractCode={}", baseUrl, contractCode);
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("monnify.enabled=true but monnify.api-key / monnify.secret-key is missing");
        }
    }

    @Override
    public InitializeTransactionResponse initializeTransaction(InitializeTransactionRequest req) {
        ensureToken();

        Map<String, Object> body = new HashMap<>();
        body.put("amount", req.getAmount());
        body.put("customerName", req.getCustomerName());
        body.put("customerEmail", req.getCustomerEmail());
        body.put("paymentReference", req.getPaymentReference());
        body.put("paymentDescription", req.getPaymentDescription());
        body.put("currencyCode", req.getCurrencyCode());
        body.put("contractCode", contractCode);
        body.put("redirectUrl", req.getRedirectUrl());

        MonnifyEnvelope<MonnifyInitResponseBody> envelope = postWithRetry(
                "/api/v1/merchant/transactions/init-transaction",
                body,
                new TypeRef<>() {});

        MonnifyInitResponseBody data = envelope.responseBody;
        return InitializeTransactionResponse.builder()
                .transactionReference(data.transactionReference)
                .paymentReference(data.paymentReference)
                .checkoutUrl(data.checkoutUrl)
                .build();
    }

    @Override
    public VerifyTransactionResponse verifyTransaction(String transactionReference) {
        ensureToken();
        // Monnify expects URL-encoded transaction reference.
        String encoded = Base64.getEncoder().encodeToString(
                transactionReference.getBytes(StandardCharsets.UTF_8));

        MonnifyEnvelope<MonnifyVerifyResponseBody> envelope = restClient.get()
                .uri("/api/v2/merchant/transactions/query?transactionReference=" + transactionReference)
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    // 401 → refresh token + retry once
                    if (response.getStatusCode().value() == 401) {
                        accessToken = null;
                        ensureToken();
                    }
                })
                .body(new org.springframework.core.ParameterizedTypeReference<MonnifyEnvelope<MonnifyVerifyResponseBody>>() {});

        if (envelope == null || envelope.responseBody == null) {
            return VerifyTransactionResponse.builder()
                    .transactionReference(transactionReference)
                    .status(MonnifyPaymentStatus.PENDING)
                    .build();
        }
        MonnifyVerifyResponseBody body = envelope.responseBody;
        return VerifyTransactionResponse.builder()
                .transactionReference(body.transactionReference)
                .paymentReference(body.paymentReference)
                .status(mapMonnifyStatus(body.paymentStatus))
                .amountPaid(body.amountPaid != null ? new BigDecimal(body.amountPaid) : null)
                .paidOn(parseMonnifyDate(body.paidOn))
                .build();
    }

    // ─── internals ──────────────────────────────────────────────────────────────

    private void ensureToken() {
        if (accessToken != null) return;
        String basic = Base64.getEncoder().encodeToString(
                (apiKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        MonnifyEnvelope<MonnifyLoginResponseBody> envelope = restClient.post()
                .uri("/api/v1/auth/login")
                .header("Authorization", "Basic " + basic)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<MonnifyEnvelope<MonnifyLoginResponseBody>>() {});
        if (envelope == null || envelope.responseBody == null) {
            throw new IllegalStateException("Monnify login returned empty body");
        }
        this.accessToken = envelope.responseBody.accessToken;
    }

    private <T> MonnifyEnvelope<T> postWithRetry(String path, Object body, TypeRef<T> typeRef) {
        try {
            return restClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(typeRef.toParameterized());
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            accessToken = null;
            ensureToken();
            return restClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(typeRef.toParameterized());
        }
    }

    private static MonnifyPaymentStatus mapMonnifyStatus(String raw) {
        if (raw == null) return MonnifyPaymentStatus.PENDING;
        return switch (raw.toUpperCase()) {
            case "PAID", "OVERPAID", "PARTIALLY_PAID" -> MonnifyPaymentStatus.PAID;
            case "FAILED", "EXPIRED", "CANCELLED" -> MonnifyPaymentStatus.FAILED;
            default -> MonnifyPaymentStatus.PENDING;
        };
    }

    private static LocalDateTime parseMonnifyDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Monnify uses ISO-8601 with millis: 2024-01-15T10:30:45.123
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("Could not parse Monnify date '{}'", raw);
            return null;
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ─── inner DTOs that match Monnify's envelope ───────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MonnifyEnvelope<T> {
        public boolean requestSuccessful;
        public String responseMessage;
        public String responseCode;
        public T responseBody;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MonnifyLoginResponseBody {
        public String accessToken;
        public long expiresIn;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MonnifyInitResponseBody {
        public String transactionReference;
        public String paymentReference;
        public String checkoutUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MonnifyVerifyResponseBody {
        public String transactionReference;
        public String paymentReference;
        public String paymentStatus;
        public String amountPaid;
        public String paidOn;
    }

    /** Helper to capture the generic envelope type at call sites. */
    private static class TypeRef<T> {
        org.springframework.core.ParameterizedTypeReference<MonnifyEnvelope<T>> toParameterized() {
            return new org.springframework.core.ParameterizedTypeReference<>() {};
        }
    }
}
