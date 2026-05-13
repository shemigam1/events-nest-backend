package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventImageResponse;
import group.moniepoint.eventsnestserver.events.dto.response.PresignCoverResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface EventImageService {

    /**
     * Generate a pre-signed PUT URL so the frontend can upload the cover image
     * directly to the storage backend (S3 or local). The permanent public URL is
     * written to {@code events.cover_image_url} immediately — no confirmation
     * step is needed because the key is chosen by the server before the upload.
     *
     * @param eventId     The event whose cover is being set.
     * @param contentType MIME type the frontend will upload (must be image/jpeg or image/png).
     * @param uploader    The authenticated user — must be the event organiser.
     */
    EventsNestResponse<PresignCoverResponse> presignCoverUpload(UUID eventId, String contentType, User uploader);

    /**
     * @deprecated Use {@link #presignCoverUpload} instead. Kept for backward
     *             compatibility with clients that still POST multipart directly.
     */
    @Deprecated
    EventsNestResponse<EventImageResponse> uploadCover(UUID eventId, MultipartFile file, User uploader);
}
