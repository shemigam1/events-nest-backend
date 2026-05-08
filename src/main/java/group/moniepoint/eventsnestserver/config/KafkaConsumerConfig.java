package group.moniepoint.eventsnestserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Wires a single message converter that all @KafkaListener handlers use.
 *
 * Why this exists: the producer side serializes to plain JSON without Spring's
 * "__TypeId__" headers (intentional — keeps the wire format portable across
 * languages and stable across refactors). With no headers, JsonDeserializer
 * has no class to target. Switching the value deserializer to StringDeserializer
 * and registering this converter lets Spring read each @KafkaListener method's
 * parameter type at runtime and Jackson-deserialize into it directly.
 *
 * One consumer factory, multiple POJO types — no per-event listener container
 * needed.
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * We construct an ObjectMapper locally (rather than autowiring the
     * web-stack default) because Spring Boot 4's autoconfig wiring for the
     * shared ObjectMapper bean isn't always available at the point Kafka
     * autoconfiguration runs. Registering JavaTimeModule explicitly handles
     * LocalDateTime fields on event payloads (e.g. BookingConfirmedEvent.confirmedAt).
     */
    @Bean
    public RecordMessageConverter recordMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new StringJsonMessageConverter(mapper);
    }
}
