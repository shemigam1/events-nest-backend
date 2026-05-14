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

    @Value("${contract-signed.kafka.topic}")
    private String contractSignedTopic;

    @Value("${audit.kafka.topic}")
    private String auditTopic;

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

    @Bean
    public NewTopic contractSignedTopic() {
        return TopicBuilder.name(contractSignedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        // Single partition — audit events must be consumed in global insertion order.
        return TopicBuilder.name(auditTopic).partitions(1).replicas(1).build();
    }
}
