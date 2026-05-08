package group.moniepoint.eventsnestserver.notifications.repository;

import group.moniepoint.eventsnestserver.notifications.model.Notification;
import group.moniepoint.eventsnestserver.notifications.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, String userId);

    boolean existsByTypeAndDedupeKey(NotificationType type, String dedupeKey);
}
