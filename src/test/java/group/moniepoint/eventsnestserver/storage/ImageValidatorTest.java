package group.moniepoint.eventsnestserver.storage;

import group.moniepoint.eventsnestserver.exception.event.InvalidEventImageException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void acceptsRealJpeg() {
        assertThatCode(() -> ImageValidator.validate("image/jpeg", 1024, JPEG))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRealPng() {
        assertThatCode(() -> ImageValidator.validate("image/png", 1024, PNG))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisallowedContentType() {
        assertThatThrownBy(() -> ImageValidator.validate("image/gif", 1024, JPEG))
                .isInstanceOf(InvalidEventImageException.class)
                .hasMessageContaining("only image/jpeg and image/png");
    }

    @Test
    void rejectsZeroSize() {
        assertThatThrownBy(() -> ImageValidator.validate("image/jpeg", 0, JPEG))
                .isInstanceOf(InvalidEventImageException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsOversize() {
        long sixMb = 6L * 1024 * 1024;
        assertThatThrownBy(() -> ImageValidator.validate("image/jpeg", sixMb, JPEG))
                .isInstanceOf(InvalidEventImageException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void rejectsSpoofedContentType() {
        // claims jpeg but actually PNG bytes
        assertThatThrownBy(() -> ImageValidator.validate("image/jpeg", 1024, PNG))
                .isInstanceOf(InvalidEventImageException.class)
                .hasMessageContaining("don't match");
    }

    @Test
    void rejectsNullBytes() {
        assertThatThrownBy(() -> ImageValidator.validate("image/jpeg", 1024, null))
                .isInstanceOf(InvalidEventImageException.class);
    }
}
