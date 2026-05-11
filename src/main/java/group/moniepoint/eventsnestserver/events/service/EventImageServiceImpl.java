package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventImageResponse;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.InvalidEventImageException;
import group.moniepoint.eventsnestserver.storage.FileStorageService;
import group.moniepoint.eventsnestserver.storage.ImageValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class EventImageServiceImpl implements EventImageService {

    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public EventsNestResponse<EventImageResponse> uploadCover(UUID eventId, MultipartFile file, User uploader) {
        Events event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
        assertIsOrganizer(eventId, uploader);

        if (file == null || file.isEmpty()) {
            throw new InvalidEventImageException("file is required");
        }

        // Sniff first 8 bytes for magic-byte validation.
        byte[] firstBytes = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(firstBytes);
            if (read < firstBytes.length) {
                // pad with zeros — validator will reject on prefix mismatch anyway
                for (int i = Math.max(read, 0); i < firstBytes.length; i++) firstBytes[i] = 0;
            }
        } catch (IOException e) {
            throw new InvalidEventImageException("could not read upload stream");
        }
        ImageValidator.validate(file.getContentType(), file.getSize(), firstBytes);

        String previousUrl = event.getCoverImageUrl();
        String extension = extensionFor(file.getContentType());
        String path = "events/" + eventId + "/cover-" + UUID.randomUUID() + "." + extension;

        String newUrl;
        try (InputStream in = file.getInputStream()) {
            newUrl = fileStorageService.store(path, file.getContentType(), file.getSize(), in);
        } catch (IOException e) {
            throw new InvalidEventImageException("upload failed: " + e.getMessage());
        }

        event.setCoverImageUrl(newUrl);
        eventRepository.saveAndFlush(event);

        // Best-effort: drop the previous file so we don't leak storage.
        if (previousUrl != null && !previousUrl.equals(newUrl)) {
            try {
                fileStorageService.delete(previousUrl);
            } catch (IOException e) {
                log.warn("failed to delete previous cover for event {}: {}", eventId, e.getMessage());
            }
        }

        EventsNestResponse<EventImageResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Cover image uploaded");
        response.setData(EventImageResponse.builder()
                .eventId(eventId)
                .coverImageUrl(newUrl)
                .build());
        return response;
    }

    private void assertIsOrganizer(UUID eventId, User user) {
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) throw new NotEventOrganizerException();
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "bin";
        };
    }
}
