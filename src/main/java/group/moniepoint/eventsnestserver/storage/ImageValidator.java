package group.moniepoint.eventsnestserver.storage;

import group.moniepoint.eventsnestserver.exception.event.InvalidEventImageException;

import java.util.Set;

/**
 * Content-type + magic-bytes + size validation for image uploads.
 *
 * Why magic bytes:
 *   The HTTP Content-Type is client-controlled. A malicious client could
 *   send {@code Content-Type: image/png} with arbitrary bytes. Checking
 *   the first few bytes of the payload makes spoofing materially harder.
 */
public final class ImageValidator {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    // JPEG: FF D8 FF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private ImageValidator() {}

    /**
     * Throws {@link InvalidEventImageException} if anything fails. Returns
     * silently on success.
     */
    public static void validate(String contentType, long sizeBytes, byte[] firstBytes) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidEventImageException("only image/jpeg and image/png are accepted");
        }
        if (sizeBytes <= 0) {
            throw new InvalidEventImageException("image is empty");
        }
        if (sizeBytes > MAX_BYTES) {
            throw new InvalidEventImageException("image exceeds 5 MB limit");
        }
        if (firstBytes == null || !magicMatches(firstBytes, contentType)) {
            throw new InvalidEventImageException("image bytes don't match the declared content type");
        }
    }

    private static boolean magicMatches(byte[] bytes, String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> startsWith(bytes, JPEG_MAGIC);
            case "image/png" -> startsWith(bytes, PNG_MAGIC);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
