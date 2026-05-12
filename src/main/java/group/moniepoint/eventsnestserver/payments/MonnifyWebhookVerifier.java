package group.moniepoint.eventsnestserver.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies the {@code monnify-signature} header on incoming webhook calls.
 *
 * Per Monnify's docs: signature = lowercase-hex(HMAC-SHA512(rawBody, secretKey)).
 * We MUST hash the raw body bytes, not the parsed JSON — any reformat would
 * change the bytes and break the signature.
 *
 * Equality check uses {@link MessageDigest#isEqual} for constant-time comparison
 * (defends against timing attacks that could otherwise leak the signature
 * byte-by-byte).
 */
@Slf4j
@Component
public class MonnifyWebhookVerifier {

    private final String secretKey;

    public MonnifyWebhookVerifier(@Value("${monnify.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("monnify.secret-key is not set — webhook signature verification will reject everything.");
        }
    }

    public boolean isValid(String rawBody, String signatureHeader) {
        if (secretKey == null || secretKey.isBlank()) return false;
        if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) return false;
        try {
            String expected = computeSignature(rawBody, secretKey);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("HMAC computation failed: {}", e.getMessage());
            return false;
        }
    }

    static String computeSignature(String body, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
