package group.moniepoint.eventsnestserver.notifications.service;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.notifications.dto.response.NotificationResponse;
import group.moniepoint.eventsnestserver.notifications.model.Notification;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository repository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(repository);
    }

    // ─── createIfAbsent ──────────────────────────────────────────────────────────

    @Test
    void createIfAbsent_persistsNotificationWhenNoneExists() {
        when(repository.existsByTypeAndDedupeKey(NotificationType.BOOKING_CONFIRMED, "booking-1"))
                .thenReturn(false);

        boolean inserted = service.createIfAbsent(
                "user-1",
                NotificationType.BOOKING_CONFIRMED,
                "booking-1",
                "Booking confirmed",
                "Two VIP seats");

        assertThat(inserted).isTrue();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(saved.getDedupeKey()).isEqualTo("booking-1");
        assertThat(saved.getTitle()).isEqualTo("Booking confirmed");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void createIfAbsent_skipsWhenAlreadyExists() {
        when(repository.existsByTypeAndDedupeKey(NotificationType.BOOKING_CONFIRMED, "booking-1"))
                .thenReturn(true);

        boolean inserted = service.createIfAbsent(
                "user-1", NotificationType.BOOKING_CONFIRMED, "booking-1", "x", "y");

        assertThat(inserted).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void createIfAbsent_skipsWhenUserIdIsNull() {
        boolean inserted = service.createIfAbsent(
                null, NotificationType.EVENT_APPROVED, "event-1", "x", "y");

        assertThat(inserted).isFalse();
        verify(repository, never()).existsByTypeAndDedupeKey(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void createIfAbsent_swallowsDuplicateUniqueViolationFromRaceCondition() {
        // existsBy* returns false, but another consumer's save committed first;
        // the unique constraint fires on our insert.
        when(repository.existsByTypeAndDedupeKey(any(), any())).thenReturn(false);
        when(repository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("uq_notifications_type_dedupe"));

        boolean inserted = service.createIfAbsent(
                "user-1", NotificationType.BOOKING_CONFIRMED, "booking-1", "x", "y");

        assertThat(inserted).isFalse();
    }

    // ─── getMyNotifications ──────────────────────────────────────────────────────

    @Test
    void getMyNotifications_returnsPagedResponseScopedToUser() {
        Notification n1 = sampleNotification("user-1", NotificationType.BOOKING_CONFIRMED, "b1");
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAllByUserIdOrderByCreatedAtDesc("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(n1), pageable, 1));

        PageResponse<NotificationResponse> response = service.getMyNotifications("user-1", pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    // ─── markAsRead ──────────────────────────────────────────────────────────────

    @Test
    void markAsRead_flipsFlagOnUserOwnedNotification() {
        UUID id = UUID.randomUUID();
        Notification n = sampleNotification("user-1", NotificationType.EVENT_APPROVED, "e1");
        n.setRead(false);
        when(repository.findByIdAndUserId(id, "user-1")).thenReturn(Optional.of(n));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        EventsNestResponse<NotificationResponse> response = service.markAsRead(id, "user-1");

        assertThat(n.isRead()).isTrue();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().isRead()).isTrue();
    }

    @Test
    void markAsRead_throwsWhenNotificationDoesNotBelongToUser() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, "intruder")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(id, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("notification not found");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Notification sampleNotification(String userId, NotificationType type, String dedupe) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(type)
                .dedupeKey(dedupe)
                .title("t")
                .message("m")
                .read(false)
                .build();
    }
}
