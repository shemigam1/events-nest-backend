package group.moniepoint.eventsnestserver.storage;

/**
 * Returned by {@link FileStorageService#presignPut}.
 *
 * @param uploadUrl  The URL the frontend should PUT the raw binary to.
 *                   For S3 this is a pre-signed S3 URL.
 *                   For local dev this points at the local upload endpoint.
 * @param publicUrl  The permanent URL that will serve the file once uploaded.
 *                   This is what gets persisted in the database.
 */
public record PresignResult(String uploadUrl, String publicUrl) {}
