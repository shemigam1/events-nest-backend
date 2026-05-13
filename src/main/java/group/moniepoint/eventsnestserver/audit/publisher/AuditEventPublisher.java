package group.moniepoint.eventsnestserver.audit.publisher;

import group.moniepoint.eventsnestserver.audit.event.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget publisher for audit events.
 *
 * <p>Failures are logged but never rethrown — a Kafka outage must not block or
 * fail the business operation that triggered the audit entry.
 *
 * <p>Records are keyed by {@code entityId} so all events for the same entity
 * land on the same partition and are consumed in order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${audit.kafka.topic}")
    private String topic;

    public void publish(AuditEvent event) {
        kafkaTemplate.send(topic, event.entityId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish audit event action={} entityType={} entityId={}",
                                  event.action(), event.entityType(), event.entityId(), ex);
                    }
                });
    }
}
