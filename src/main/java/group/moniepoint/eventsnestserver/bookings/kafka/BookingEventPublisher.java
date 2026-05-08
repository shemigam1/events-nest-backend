package group.moniepoint.eventsnestserver.bookings.kafka;

import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${booking.kafka.topic}")
    private String topic;

    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        kafkaTemplate.send(topic, event.bookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish booking.confirmed for booking {}", event.bookingId(), ex);
                    }
                });
    }
}
