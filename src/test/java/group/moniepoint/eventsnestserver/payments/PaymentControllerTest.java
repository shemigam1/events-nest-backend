package group.moniepoint.eventsnestserver.payments;

import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.bookings.service.BookingService;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.booking.BookingNotFoundException;
import group.moniepoint.eventsnestserver.payments.controller.PaymentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String SECRET = "test-secret-key-very-long-and-secret-1234567890";

    @Mock
    private BookingService bookingService;

    private MonnifyWebhookVerifier verifier;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        verifier = new MonnifyWebhookVerifier(SECRET);
        controller = new PaymentController(bookingService, verifier);
    }

    @Test
    void webhookRejectsBadSignature() {
        String body = "{\"eventType\":\"SUCCESSFUL_TRANSACTION\",\"eventData\":{\"transactionReference\":\"X\"}}";
        ResponseEntity<?> res = controller.webhook(body, "deadbeef");

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        verify(bookingService, never()).finalizeBookingPayment(any());
        verify(bookingService, never()).markBookingFailed(any(), any());
    }

    @Test
    void webhookFinalizesOnSuccessEvent() throws Exception {
        String body = "{\"eventType\":\"SUCCESSFUL_TRANSACTION\",\"eventData\":{\"transactionReference\":\"REF-1\"}}";
        String sig = MonnifyWebhookVerifier.computeSignature(body, SECRET);

        when(bookingService.finalizeBookingPayment("REF-1"))
                .thenReturn(stubBookingResponse());

        ResponseEntity<?> res = controller.webhook(body, sig);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(bookingService).finalizeBookingPayment("REF-1");
        verify(bookingService, never()).markBookingFailed(any(), any());
    }

    @Test
    void webhookMarksFailedOnFailureEvent() throws Exception {
        String body = "{\"eventType\":\"FAILED_TRANSACTION\",\"eventData\":{\"transactionReference\":\"REF-2\"}}";
        String sig = MonnifyWebhookVerifier.computeSignature(body, SECRET);

        when(bookingService.markBookingFailed(eq("REF-2"), any()))
                .thenReturn(stubBookingResponse());

        ResponseEntity<?> res = controller.webhook(body, sig);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(bookingService).markBookingFailed(eq("REF-2"), any());
        verify(bookingService, never()).finalizeBookingPayment(any());
    }

    @Test
    void webhookIgnoresUnknownEventType() throws Exception {
        String body = "{\"eventType\":\"REFUND_NOTICE\",\"eventData\":{\"transactionReference\":\"REF-3\"}}";
        String sig = MonnifyWebhookVerifier.computeSignature(body, SECRET);

        ResponseEntity<?> res = controller.webhook(body, sig);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(bookingService, never()).finalizeBookingPayment(any());
        verify(bookingService, never()).markBookingFailed(any(), any());
    }

    @Test
    void webhookRejectsMalformedBody() throws Exception {
        String body = "not json";
        String sig = MonnifyWebhookVerifier.computeSignature(body, SECRET);

        ResponseEntity<?> res = controller.webhook(body, sig);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void webhookReturns200EvenWhenBookingUnknown() throws Exception {
        // Monnify retries non-2xx forever. If we don't recognise the ref
        // (stale test webhook, etc.) we still ack the delivery.
        String body = "{\"eventType\":\"SUCCESSFUL_TRANSACTION\",\"eventData\":{\"transactionReference\":\"REF-GONE\"}}";
        String sig = MonnifyWebhookVerifier.computeSignature(body, SECRET);
        when(bookingService.finalizeBookingPayment("REF-GONE"))
                .thenThrow(new BookingNotFoundException());

        ResponseEntity<?> res = controller.webhook(body, sig);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void verifyEndpointDelegatesToFinalize() {
        when(bookingService.finalizeBookingPayment("REF-VERIFY"))
                .thenReturn(stubBookingResponse());

        ResponseEntity<?> res = controller.verify("REF-VERIFY");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(bookingService).finalizeBookingPayment("REF-VERIFY");
    }

    private EventsNestResponse<BookingResponse> stubBookingResponse() {
        EventsNestResponse<BookingResponse> r = new EventsNestResponse<>();
        r.setSuccess(true);
        r.setMessage("ok");
        r.setData(BookingResponse.builder().build());
        return r;
    }
}
