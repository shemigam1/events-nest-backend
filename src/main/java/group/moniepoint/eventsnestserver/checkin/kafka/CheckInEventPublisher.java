package group.moniepoint.eventsnestserver.checkin.kafka;

import group.moniepoint.eventsnestserver.checkin.event.TicketCheckedInEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${checkin.kafka.topic}")
    private String topic;

    public void publish(TicketCheckedInEvent event) {
        kafkaTemplate.send(topic, event.ticketId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ticket.checked_in for ticket {}", event.ticketId(), ex);
                    }
                });
    }
}
