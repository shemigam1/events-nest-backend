package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventImageResponse;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.service.EventImageServiceImpl;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.InvalidEventImageException;
import group.moniepoint.eventsnestserver.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventImageServiceTest {

    @Mock
    private EventRespository eventRepository;
    @Mock
    private EventMembershipRepository membershipRepository;
    @Mock
    private FileStorageService fileStorageService;

    private EventImageServiceImpl service;

    private User organizer;
    private Events event;
    private UUID eventId;

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0};

    @BeforeEach
    void setUp() {
        service = new EventImageServiceImpl(eventRepository, membershipRepository, fileStorageService);

        organizer = User.builder()
                .id("organizer001")
                .email("o@example.com")
                .firstName("Or")
                .lastName("Ga")
                .passwordHash("h")
                .role(Role.USER)
                .build();

        eventId = UUID.randomUUID();
        event = Events.builder()
                .id(eventId)
                .title("Test Event")
                .build();
    }

    private MultipartFile validJpeg() {
        return new MockMultipartFile("file", "cover.jpg", "image/jpeg", JPEG_HEADER);
    }

    @Test
    void uploadCoverPersistsAndReturnsUrl() throws Exception {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(fileStorageService.store(anyString(), eq("image/jpeg"), anyLong(), any()))
                .thenReturn("https://cdn.example.com/events/" + eventId + "/cover.jpg");

        EventsNestResponse<EventImageResponse> response = service.uploadCover(eventId, validJpeg(), organizer);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCoverImageUrl())
                .isEqualTo("https://cdn.example.com/events/" + eventId + "/cover.jpg");

        ArgumentCaptor<Events> captor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCoverImageUrl())
                .isEqualTo("https://cdn.example.com/events/" + eventId + "/cover.jpg");
    }

    @Test
    void uploadCoverDeletesPreviousImage() throws Exception {
        event.setCoverImageUrl("https://cdn.example.com/events/" + eventId + "/old.jpg");
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);
        when(fileStorageService.store(anyString(), anyString(), anyLong(), any()))
                .thenReturn("https://cdn.example.com/events/" + eventId + "/new.jpg");

        service.uploadCover(eventId, validJpeg(), organizer);

        verify(fileStorageService).delete("https://cdn.example.com/events/" + eventId + "/old.jpg");
    }

    @Test
    void uploadCoverThrowsWhenEventMissing() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadCover(eventId, validJpeg(), organizer))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void uploadCoverRejectsNonOrganizer() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.uploadCover(eventId, validJpeg(), organizer))
                .isInstanceOf(NotEventOrganizerException.class);
    }

    @Test
    void uploadCoverRejectsEmptyFile() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        MultipartFile empty = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.uploadCover(eventId, empty, organizer))
                .isInstanceOf(InvalidEventImageException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    void uploadCoverRejectsSpoofedContentType() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, organizer.getId(), EventRole.ORGANIZER))
                .thenReturn(true);

        // claims JPEG but bytes are gibberish
        MultipartFile spoof = new MockMultipartFile(
                "file", "evil.jpg", "image/jpeg", new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});

        assertThatThrownBy(() -> service.uploadCover(eventId, spoof, organizer))
                .isInstanceOf(InvalidEventImageException.class);

        verify(eventRepository, never()).saveAndFlush(any(Events.class));
    }
}
