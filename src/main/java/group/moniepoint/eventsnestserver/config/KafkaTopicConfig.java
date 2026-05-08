package group.moniepoint.eventsnestserver.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${checkin.kafka.topic}")
    private String checkinTopic;

    @Value("${booking.kafka.topic}")
    private String bookingTopic;

    @Bean
    public NewTopic ticketCheckedInTopic() {
        return TopicBuilder.name(checkinTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(bookingTopic).partitions(3).replicas(1).build();
    }
}
