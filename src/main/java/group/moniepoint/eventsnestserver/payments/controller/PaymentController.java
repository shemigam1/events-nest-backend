package group.moniepoint.eventsnestserver.payments.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import group.moniepoint.eventsnestserver.bookings.service.BookingService;
import group.moniepoint.eventsnestserver.exception.booking.BookingNotFoundException;
import group.moniepoint.eventsnestserver.payments.MonnifyWebhookVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/monnify")
@AllArgsConstructor
@Tag(name = "Payments", description = "Monnify webhook + manual verification")
public class PaymentController {

    private final BookingService bookingService;
    private final MonnifyWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Monnify posts here when a transaction completes (or fails). Public —
     * we can't put it behind auth because Monnify wouldn't have a JWT.
     * Authenticity is enforced by HMAC-SHA512 signature on the raw body.
     */
    @Operation(summary = "Monnify webhook receiver. HMAC-verified. Idempotent.")
    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<?> webhook(@RequestBody String rawBody,
                                     @RequestHeader(value = "monnify-signature", required = false) String signature) {
        if (!webhookVerifier.isValid(rawBody, signature)) {
            log.warn("rejected Monnify webhook — signature mismatch");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MonnifyWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, MonnifyWebhookPayload.class);
        } catch (Exception e) {
            log.warn("could not parse Monnify webhook body: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        if (payload.eventData == null || payload.eventData.transactionReference == null) {
            return ResponseEntity.badRequest().build();
        }

        String ref = payload.eventData.transactionReference;
        String type = payload.eventType != null ? payload.eventType.toUpperCase() : "";
        log.info("Monnify webhook — type={}, transactionReference={}", type, ref);

        // ALWAYS return 200 to a well-signed webhook, even when the
        // referenced booking is unknown to us (stale test webhook,
        // already-deleted booking, etc.). Otherwise Monnify retries
        // the same event indefinitely.
        try {
            if (type.contains("SUCCESS") || type.equals("SUCCESSFUL_TRANSACTION")) {
                bookingService.finalizeBookingPayment(ref);
            } else if (type.contains("FAIL") || type.contains("CANCEL") || type.contains("EXPIR")) {
                bookingService.markBookingFailed(ref, type);
            } else {
                log.info("Monnify webhook — ignoring unhandled event type {}", type);
            }
        } catch (BookingNotFoundException e) {
            log.warn("Monnify webhook — unknown transactionReference {}; acknowledging anyway", ref);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Fallback verification path. Lets the frontend (or a manual operator)
     * poll the gateway and finalize a booking if the webhook was delayed
     * or dropped. Same idempotent finalize path as the webhook.
     */
    @Operation(
            summary = "Manually verify and finalize a transaction. Used as a webhook fallback.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/verify/{transactionReference}")
    public ResponseEntity<?> verify(@PathVariable String transactionReference) {
        return ResponseEntity.ok(bookingService.finalizeBookingPayment(transactionReference));
    }

    // ─── inner DTO for inbound webhook ──────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MonnifyWebhookPayload {
        public String eventType;
        public EventData eventData;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class EventData {
            public String transactionReference;
            public String paymentReference;
            public String paymentStatus;
        }
    }
}
