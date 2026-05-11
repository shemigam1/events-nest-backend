package group.moniepoint.eventsnestserver.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonnifyWebhookVerifierTest {

    private static final String SECRET = "test-secret-key-very-long-and-secret-1234567890";
    private static final String BODY = "{\"eventType\":\"SUCCESSFUL_TRANSACTION\",\"eventData\":{\"transactionReference\":\"MNFY|001|123\"}}";

    @Test
    void acceptsValidSignature() throws Exception {
        MonnifyWebhookVerifier verifier = new MonnifyWebhookVerifier(SECRET);
        String sig = MonnifyWebhookVerifier.computeSignature(BODY, SECRET);

        assertThat(verifier.isValid(BODY, sig)).isTrue();
    }

    @Test
    void acceptsValidSignatureMixedCase() throws Exception {
        // Some clients send uppercase hex; we normalize.
        MonnifyWebhookVerifier verifier = new MonnifyWebhookVerifier(SECRET);
        String sig = MonnifyWebhookVerifier.computeSignature(BODY, SECRET).toUpperCase();

        assertThat(verifier.isValid(BODY, sig)).isTrue();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        MonnifyWebhookVerifier verifier = new MonnifyWebhookVerifier(SECRET);
        String sig = MonnifyWebhookVerifier.computeSignature(BODY, SECRET);

        String tampered = BODY.replace("SUCCESSFUL_TRANSACTION", "FAILED_TRANSACTION");
        assertThat(verifier.isValid(tampered, sig)).isFalse();
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        MonnifyWebhookVerifier verifier = new MonnifyWebhookVerifier(SECRET);
        String sigFromWrongKey = MonnifyWebhookVerifier.computeSignature(BODY, "different-key");

        assertThat(verifier.isValid(BODY, sigFromWrongKey)).isFalse();
    }

    @Test
    void rejectsEmptySignature() {
        MonnifyWebhookVerifier verifier = new MonnifyWebhookVerifier(SECRET);
        assertThat(verifier.isValid(BODY, "")).isFalse();
        assertThat(verifier.isValid(BODY, null)).isFalse();
    }

    @Test
    void rejectsAllWhenSecretMissing() throws Exception {
        MonnifyWebhookVerifier verifier = new MonnifyWebhookVerifier("");
        String sig = MonnifyWebhookVerifier.computeSignature(BODY, SECRET);
        assertThat(verifier.isValid(BODY, sig)).isFalse();
    }
}
