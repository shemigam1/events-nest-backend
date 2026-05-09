package group.moniepoint.eventsnestserver.common.logging;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Copies the current MDC correlation ID into every Kafka record as a header,
 * so the downstream consumer (and any further hops) can keep the same ID in
 * its MDC. Without this, the Kafka boundary breaks the trace — the consumer
 * thread has no inherent knowledge of the request that triggered the publish.
 *
 * Wired via {@code spring.kafka.producer.properties.interceptor.classes} —
 * see application.properties.
 */
public class CorrelationIdProducerInterceptor implements ProducerInterceptor<String, Object> {

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (id != null && !id.isBlank()) {
            record.headers().add(CorrelationIdFilter.HEADER,
                    id.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op — instantiated by Kafka via reflection
    }
}
