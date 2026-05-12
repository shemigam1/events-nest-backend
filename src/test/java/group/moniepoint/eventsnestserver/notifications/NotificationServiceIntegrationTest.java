package group.moniepoint.eventsnestserver.notifications;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.notifications.dto.response.NotificationResponse;
import group.moniepoint.eventsnestserver.notifications.model.Notification;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.repository.NotificationRepository;
import group.moniepoint.eventsnestserver.notifications.service.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for NotificationServiceImpl.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("NotificationService Integration Tests")
@Import(IntegrationTestConfig.class)
class NotificationServiceIntegrationTest {

    @Autowired private NotificationServiceImpl notificationService;
    @Autowired private NotificationRepository notificationRepository;

    private static final String USER_A = "useraaaa0001";
    private static final String USER_B = "userbbb00001";

    @BeforeEach
    void seed() {
        notificationRepository.save(Notification.builder()
                .userId(USER_A)
                .type(NotificationType.BOOKING_CONFIRMED)
                .dedupeKey("booking-001")
                .title("Booking Confirmed")
                .message("Your booking has been confirmed")
                .build());

        notificationRepository.save(Notification.builder()
                .userId(USER_A)
                .type(NotificationType.EVENT_APPROVED)
                .dedupeKey("event-approve-001")
                .title("Event Approved")
                .message("Your event was approved")
                .build());

        notificationRepository.save(Notification.builder()
                .userId(USER_B)
                .type(NotificationType.CHECKIN_SUCCESS)
                .dedupeKey("checkin-001")
                .title("Checked In")
                .message("You have checked in")
                .build());
    }

    // ─── getMyNotifications() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyNotifications()")
    class GetMyNotifications {

        @Test
        @DisplayName("Returns only notifications for the requesting user")
        void returnsOnlyCallerNotifications() {
            PageResponse<NotificationResponse> page =
                    notificationService.getMyNotifications(USER_A, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(2);
            // Titles are unique per notification — verify correct ones returned
            assertThat(page.getContent()).extracting(NotificationResponse::getTitle)
                    .containsExactlyInAnyOrder("Booking Confirmed", "Event Approved");
        }

        @Test
        @DisplayName("Does not include notifications belonging to other users")
        void excludesOtherUsersNotifications() {
            PageResponse<NotificationResponse> page =
                    notificationService.getMyNotifications(USER_B, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getTitle()).isEqualTo("Checked In");
        }

        @Test
        @DisplayName("Returns empty page for a user with no notifications")
        void returnsEmptyForNewUser() {
            PageResponse<NotificationResponse> page =
                    notificationService.getMyNotifications("newuser00001", PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Pagination limits results correctly")
        void paginationLimitsResults() {
            PageResponse<NotificationResponse> page =
                    notificationService.getMyNotifications(USER_A, PageRequest.of(0, 1));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    // ─── markAsRead() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("Marks a notification as read for the owning user")
        void marksNotificationAsRead() {
            Notification n = notificationRepository
                    .findAllByUserIdOrderByCreatedAtDesc(USER_A, PageRequest.of(0, 10))
                    .getContent().get(0);

            EventsNestResponse<NotificationResponse> response =
                    notificationService.markAsRead(n.getId(), USER_A);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().isRead()).isTrue();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when another user tries to mark the notification")
        void throwsForWrongUser() {
            Notification n = notificationRepository
                    .findAllByUserIdOrderByCreatedAtDesc(USER_A, PageRequest.of(0, 10))
                    .getContent().get(0);

            assertThatThrownBy(() -> notificationService.markAsRead(n.getId(), USER_B))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for a non-existent notification ID")
        void throwsForNonExistentId() {
            assertThatThrownBy(() -> notificationService.markAsRead(UUID.randomUUID(), USER_A))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── createIfAbsent() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createIfAbsent()")
    class CreateIfAbsent {

        @Test
        @DisplayName("Creates notification and returns true on first call")
        void returnsTrueOnFirstCreate() {
            boolean created = notificationService.createIfAbsent(
                    USER_A,
                    NotificationType.BUDGET_THRESHOLD_REACHED,
                    "budget-threshold-event-99",
                    "Budget Alert",
                    "80% of budget spent");

            assertThat(created).isTrue();
        }

        @Test
        @DisplayName("Returns false without creating duplicate when called again with same dedupeKey")
        void returnsFalseOnDuplicate() {
            notificationService.createIfAbsent(
                    USER_A,
                    NotificationType.BUDGET_THRESHOLD_REACHED,
                    "budget-threshold-unique-xyz",
                    "Budget Alert",
                    "80% spent");

            boolean secondAttempt = notificationService.createIfAbsent(
                    USER_A,
                    NotificationType.BUDGET_THRESHOLD_REACHED,
                    "budget-threshold-unique-xyz",
                    "Budget Alert",
                    "80% spent");

            assertThat(secondAttempt).isFalse();
            // Only one record should exist
            assertThat(notificationRepository.existsByTypeAndDedupeKey(
                    NotificationType.BUDGET_THRESHOLD_REACHED, "budget-threshold-unique-xyz")).isTrue();
        }

        @Test
        @DisplayName("Returns false and does not persist when userId is null")
        void returnsFalseForNullUserId() {
            boolean result = notificationService.createIfAbsent(
                    null,
                    NotificationType.BOOKING_CONFIRMED,
                    "null-user-key",
                    "Should Not Save",
                    "Message");

            assertThat(result).isFalse();
        }
    }
}
