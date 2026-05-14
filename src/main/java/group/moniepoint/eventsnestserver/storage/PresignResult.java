package group.moniepoint.eventsnestserver.storage;

/**
 * Returned by {@link FileStorageService#presignPut}.
 *
 * @param uploadUrl  Short-lived PUT URL the frontend sends file bytes to.
 *                   S3: presigned PUT. Local dev: one-time token endpoint.
 * @param publicUrl  Permanent URL persisted in the database.
 *                   For S3 this is the public/CDN URL (no signature).
 * @param previewUrl Short-lived GET URL for in-browser preview immediately
 *                   after upload, before the object is publicly accessible.
 *                   S3: presigned GET. Local dev: same as publicUrl.
 */
public record PresignResult(String uploadUrl, String publicUrl, String previewUrl) {}
