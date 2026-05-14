package group.moniepoint.eventsnestserver.payments.controller;

import group.moniepoint.eventsnestserver.bookings.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-dev-only stub payment handler. Active only when monnify.enabled=false.
 *
 * Flow:
 *   1. StubMonnifyClient generates a checkoutUrl pointing here.
 *   2. Frontend redirects the browser to this endpoint.
 *   3. This endpoint finalizes the booking (PENDING → PAID, issues tickets).
 *   4. Redirects the browser to the frontend payment-result page with
 *      transactionReference + status=SUCCESS already set — so the result
 *      page's verify call returns PAID instantly.
 */
@Slf4j
@RestController
@ConditionalOnProperty(name = "monnify.enabled", havingValue = "false", matchIfMissing = true)
public class StubPaymentController {

    private final BookingService bookingService;
    private final String paymentResultUrl;

    public StubPaymentController(
            BookingService bookingService,
            @Value("${monnify.redirect-url:http://localhost:5173/payment-result}") String paymentResultUrl) {
        this.bookingService = bookingService;
        this.paymentResultUrl = paymentResultUrl;
    }

    @GetMapping("/api/v1/payments/monnify/stub-pay")
    public ResponseEntity<Void> stubPay(@RequestParam String ref) {
        log.info("Stub payment — auto-finalizing booking for transactionReference={}", ref);
        try {
            bookingService.finalizeBookingPayment(ref);
        } catch (Exception e) {
            // Already PAID (idempotent) or booking not found — still redirect so
            // the frontend can show the appropriate result screen.
            log.warn("Stub payment finalize skipped for ref={}: {}", ref, e.getMessage());
        }

        String location = paymentResultUrl
                + "?transactionReference=" + ref
                + "&status=SUCCESS";

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
