package com.yuno.processor.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.common.constants.AppConstants;
import com.yuno.processor.domain.event.PaymentDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(PaymentDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(AppConstants.PAYMENT_EVENTS_TOPIC, event.getPaymentId(), payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish payment event. paymentId={}, eventType={}",
                            event.getPaymentId(), event.getEventType(), ex);
                } else {
                    log.info("Payment event published. paymentId={}, eventType={}, offset={}",
                            event.getPaymentId(), event.getEventType(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment event. paymentId={}", event.getPaymentId(), e);
        }
    }
}
