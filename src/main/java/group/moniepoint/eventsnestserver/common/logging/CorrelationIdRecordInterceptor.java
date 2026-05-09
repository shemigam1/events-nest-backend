package group.moniepoint.eventsnestserver.common.logging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Counterpart to {@link CorrelationIdProducerInterceptor}. Pulls the
 * {@code X-Correlation-Id} header off the inbound record and pushes it into
 * MDC for the duration of the {@code @KafkaListener} method invocation, so
 * consumer-side logs (audit insert, email enqueue, SSE push) join up with
 * the originating HTTP request log lines.
 *
 * If a record arrives without the header (e.g. produced by an older client
 * before this change shipped), we mint a fresh UUID so the consumer-side
 * logs still correlate among themselves for that one record.
 *
 * Wired via {@code ContainerCustomizer} in {@code KafkaConsumerConfig}.
 */
@Component
public class CorrelationIdRecordInterceptor implements RecordInterceptor<String, Object> {

    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record,
                                                     Consumer<String, Object> consumer) {
        Header h = record.headers().lastHeader(CorrelationIdFilter.HEADER);
        String id = (h != null && h.value() != null && h.value().length > 0)
                ? new String(h.value(), StandardCharsets.UTF_8)
                : "kafka-" + UUID.randomUUID();
        MDC.put(CorrelationIdFilter.MDC_KEY, id);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Override
    public void failure(ConsumerRecord<String, Object> record, Exception exception,
                        Consumer<String, Object> consumer) {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }
}
