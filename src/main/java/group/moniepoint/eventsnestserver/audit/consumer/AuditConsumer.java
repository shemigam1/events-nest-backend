package group.moniepoint.eventsnestserver.audit.consumer;

import group.moniepoint.eventsnestserver.audit.event.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes audit events from the {@code audit.events} topic and emits one
 * structured log line per event.
 *
 * <p>The logstash encoder (already on the classpath) serialises every Logback
 * event as a JSON object — MDC keys become top-level fields in that JSON, so
 * each audit line is fully queryable in any log aggregator (Loki, CloudWatch,
 * Datadog, etc.) without any extra parsing.
 *
 * <p>The Kafka topic itself (with its configured retention) is the durable
 * audit trail. The structured log output is a searchable secondary view.
 *
 * <p>Uses its own consumer group ({@code audit-consumer-group}) so audit
 * consumption is independent of the notifications consumer group and both
 * always receive every record.
 */
@Component
@Slf4j
public class AuditConsumer {

    @KafkaListener(topics = "${audit.kafka.topic}", groupId = "audit-consumer-group")
    public void consume(AuditEvent event) {
        MDC.put("auditAction",     event.action().name());
        MDC.put("auditEntityType", event.entityType().name());
        MDC.put("auditEntityId",   event.entityId());
        MDC.put("auditActorId",    event.actorId() != null ? event.actorId() : "anonymous");
        MDC.put("auditActorRole",  event.actorRole() != null ? event.actorRole() : "none");
        try {
            log.info("AUDIT {} on {} {} by {} detail={}",
                    event.action(),
                    event.entityType(),
                    event.entityId(),
                    event.actorId() != null ? event.actorId() : "anonymous",
                    event.detail());
        } finally {
            MDC.remove("auditAction");
            MDC.remove("auditEntityType");
            MDC.remove("auditEntityId");
            MDC.remove("auditActorId");
            MDC.remove("auditActorRole");
        }
    }
}
