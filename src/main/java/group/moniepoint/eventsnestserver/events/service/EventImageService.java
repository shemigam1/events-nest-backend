package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface EventImageService {

    /**
     * Validate and persist a cover image for the event. Replaces any
     * existing cover (the old file is deleted best-effort). The new URL
     * is written back to {@code events.cover_image_url}.
     */
    EventsNestResponse<EventImageResponse> uploadCover(UUID eventId, MultipartFile file, User uploader);
}
