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

    @Value("${event-approved.kafka.topic}")
    private String eventApprovedTopic;

    @Value("${event-rejected.kafka.topic}")
    private String eventRejectedTopic;

    @Bean
    public NewTopic ticketCheckedInTopic() {
        return TopicBuilder.name(checkinTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(bookingTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic eventApprovedTopic() {
        return TopicBuilder.name(eventApprovedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic eventRejectedTopic() {
        return TopicBuilder.name(eventRejectedTopic).partitions(3).replicas(1).build();
    }
}
