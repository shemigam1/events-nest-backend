package group.moniepoint.eventsnestserver.notifications.service;

import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.notifications.dto.response.NotificationResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    PageResponse<NotificationResponse> getMyNotifications(String userId, Pageable pageable);

    EventsNestResponse<NotificationResponse> markAsRead(UUID notificationId, String userId);
}
