package group.moniepoint.eventsnestserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Single, application-wide ObjectMapper.
 *
 * Spring Boot 4's autoconfig wiring for the shared Jackson ObjectMapper isn't
 * always available at the moment our beans (KafkaConsumerConfig, EmailOutbox,
 * EmailJobPoller, etc.) initialise — registering one explicitly here avoids a
 * fragile boot-order dependency and ensures everything serialises with the
 * same rules:
 *
 *   - JavaTimeModule for {@code LocalDateTime} on event records and DTOs
 *   - WRITE_DATES_AS_TIMESTAMPS off so dates round-trip as ISO-8601 strings
 *
 * Marked {@code @Primary} so any future Jackson autoconfig that registers a
 * second ObjectMapper doesn't quietly take precedence.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
