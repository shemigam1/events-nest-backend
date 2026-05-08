package group.moniepoint.eventsnestserver.admin.kafka;

import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${event-approved.kafka.topic}")
    private String approvedTopic;

    @Value("${event-rejected.kafka.topic}")
    private String rejectedTopic;

    public void publishApproved(EventApprovedEvent event) {
        kafkaTemplate.send(approvedTopic, event.eventId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event.approved for event {}", event.eventId(), ex);
                    }
                });
    }

    public void publishRejected(EventRejectedEvent event) {
        kafkaTemplate.send(rejectedTopic, event.eventId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event.rejected for event {}", event.eventId(), ex);
                    }
                });
    }
}
