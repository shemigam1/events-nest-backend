package group.moniepoint.eventsnestserver.audit.event;

import group.moniepoint.eventsnestserver.audit.AuditAction;
import group.moniepoint.eventsnestserver.audit.AuditEntityType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka message payload for every auditable action in the system.
 *
 * <p>Produced by {@link group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher}
 * and consumed by {@link group.moniepoint.eventsnestserver.audit.consumer.AuditConsumer}.
 *
 * <p>All fields are nullable except {@code action}, {@code entityType}, and {@code entityId}.
 * {@code actorId} and {@code actorRole} are null for unauthenticated events (LOGIN_FAILURE).
 */
public record AuditEvent(
        String          actorId,
        String          actorRole,
        AuditAction     action,
        AuditEntityType entityType,
        String          entityId,
        Map<String, Object> detail,
        LocalDateTime   occurredAt
) {
    /**
     * Convenience factory — stamps {@code occurredAt} to now so call sites
     * don't have to pass a timestamp themselves.
     */
    public static AuditEvent of(String actorId, String actorRole,
                                AuditAction action,
                                AuditEntityType entityType, String entityId,
                                Map<String, Object> detail) {
        return new AuditEvent(actorId, actorRole, action,
                              entityType, entityId, detail, LocalDateTime.now());
    }
}
