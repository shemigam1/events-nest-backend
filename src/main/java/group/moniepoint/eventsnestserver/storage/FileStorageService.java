package group.moniepoint.eventsnestserver.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Storage abstraction for all binary uploads (event cover images, vendor
 * portfolio images later, contract PDFs, etc.). Two implementations:
 *   * {@link LocalFileStorageService}  — active when {@code app.storage.type=local}
 *   * {@link S3FileStorageService}     — active when {@code app.storage.type=s3}
 */
public interface FileStorageService {

    /**
     * Persist a file under the given logical path and return the public URL
     * by which it can be fetched (or referenced from the database).
     *
     * @param path        Logical path inside the bucket / uploads dir. Should
     *                    NOT contain a leading slash. Example: {@code events/<uuid>/cover.jpg}.
     * @param contentType MIME type of the upload (e.g. {@code image/jpeg}).
     * @param size        Byte size of the upload, for content-length headers.
     * @param data        Stream that yields the bytes. Will be consumed.
     * @return A URL string the frontend can render directly.
     */
    String store(String path, String contentType, long size, InputStream data) throws IOException;

    /**
     * Generate a pre-signed PUT URL that the frontend can use to upload a file
     * directly to the storage backend (S3 or local) without routing bytes
     * through this server.
     *
     * @param key         The logical storage key / path for the object.
     *                    Example: {@code events/<uuid>/cover-<random>.jpg}
     * @param contentType MIME type the frontend must send in the Content-Type
     *                    header of its PUT request (must match the signed value).
     * @return A {@link PresignResult} containing the short-lived upload URL and
     *         the permanent public URL to persist in the database.
     */
    PresignResult presignPut(String key, String contentType);

    /**
     * Best-effort delete. Implementations should swallow "not found" cases
     * silently; callers should not depend on the return value other than the
     * absence of a thrown {@link IOException}.
     */
    void delete(String url) throws IOException;
}
