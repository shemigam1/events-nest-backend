package group.moniepoint.eventsnestserver.notifications.service;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.notifications.dto.response.NotificationResponse;
import group.moniepoint.eventsnestserver.notifications.model.Notification;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import group.moniepoint.eventsnestserver.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(String userId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from);
    }

    @Override
    @Transactional
    public EventsNestResponse<NotificationResponse> markAsRead(UUID notificationId, String userId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("notification not found"));
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);

        EventsNestResponse<NotificationResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("notification marked as read");
        response.setData(NotificationResponse.from(saved));
        return response;
    }

    /**
     * Idempotent insert. Returns true if inserted, false if a notification with
     * the same {@code type + dedupeKey} already exists. Race-safe because the
     * unique constraint catches the duplicate even if the {@code existsBy*} read
     * happens before another consumer commits.
     */
    @Transactional
    public boolean createIfAbsent(String userId,
                                  NotificationType type,
                                  String dedupeKey,
                                  String title,
                                  String message) {
        if (userId == null) {
            log.warn("Skipping {} notification with null userId (dedupeKey={})", type, dedupeKey);
            return false;
        }
        if (notificationRepository.existsByTypeAndDedupeKey(type, dedupeKey)) {
            return false;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .type(type)
                    .dedupeKey(dedupeKey)
                    .title(title)
                    .message(message)
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            // Concurrent consumer beat us to it. Honour at-least-once delivery contract.
            log.debug("Duplicate {} notification suppressed (dedupeKey={})", type, dedupeKey);
            return false;
        }
    }
}
