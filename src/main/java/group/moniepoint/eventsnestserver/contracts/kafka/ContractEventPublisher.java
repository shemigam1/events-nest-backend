package group.moniepoint.eventsnestserver.contracts.kafka;

import group.moniepoint.eventsnestserver.contracts.event.ContractSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${contract-signed.kafka.topic}")
    private String topic;

    public void publishContractSigned(ContractSignedEvent event) {
        kafkaTemplate.send(topic, event.contractId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish contract.signed for contract {}", event.contractId(), ex);
                    }
                });
    }
}
