//package group.moniepoint.eventsnestserver.payments;
//
//import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionRequest;
//import group.moniepoint.eventsnestserver.payments.dto.InitializeTransactionResponse;
//import group.moniepoint.eventsnestserver.payments.dto.VerifyTransactionResponse;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class StubMonnifyClientTest {
//
//    private final StubMonnifyClient client = new StubMonnifyClient("http://localhost:5173");
//
//    @Test
//    void initializeTransactionReturnsCheckoutUrlAndSyntheticReference() {
//        InitializeTransactionRequest req = InitializeTransactionRequest.builder()
//                .amount(new BigDecimal("5000.00"))
//                .currencyCode("NGN")
//                .paymentReference(UUID.randomUUID().toString())
//                .customerEmail("a@b.com")
//                .customerName("Alpha Beta")
//                .paymentDescription("Tickets")
//                .redirectUrl("http://localhost:5173/payment-result")
//                .build();
//
//        InitializeTransactionResponse res = client.initializeTransaction(req);
//
//        assertThat(res.getTransactionReference()).startsWith("STUB-");
//        assertThat(res.getPaymentReference()).isEqualTo(req.getPaymentReference());
//        assertThat(res.getCheckoutUrl()).contains("/stub-payment").contains(res.getTransactionReference());
//    }
//
//    @Test
//    void verifyTransactionReportsPaidForPreviouslyInitiatedRef() {
//        InitializeTransactionRequest req = InitializeTransactionRequest.builder()
//                .amount(new BigDecimal("1000.00"))
//                .currencyCode("NGN")
//                .paymentReference(UUID.randomUUID().toString())
//                .customerEmail("a@b.com")
//                .customerName("A B")
//                .build();
//        InitializeTransactionResponse init = client.initializeTransaction(req);
//
//        VerifyTransactionResponse verify = client.verifyTransaction(init.getTransactionReference());
//
//        assertThat(verify.getStatus()).isEqualTo(MonnifyPaymentStatus.PAID);
//        assertThat(verify.getAmountPaid()).isEqualByComparingTo("1000.00");
//        assertThat(verify.getPaidOn()).isNotNull();
//    }
//
//    @Test
//    void verifyTransactionReportsFailedForUnknownRef() {
//        VerifyTransactionResponse verify = client.verifyTransaction("UNKNOWN-ref-xyz");
//
//        assertThat(verify.getStatus()).isEqualTo(MonnifyPaymentStatus.FAILED);
//        assertThat(verify.getAmountPaid()).isNull();
//    }
//}
