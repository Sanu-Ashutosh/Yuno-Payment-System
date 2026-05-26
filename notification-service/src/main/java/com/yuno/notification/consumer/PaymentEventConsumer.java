package com.yuno.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.common.constants.AppConstants;
import com.yuno.notification.service.WebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final WebhookDeliveryService webhookDeliveryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = AppConstants.PAYMENT_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.info("Received payment event. key={}, offset={}, partition={}",
                record.key(), record.offset(), record.partition());
        try {
            Map<?, ?> event = objectMapper.readValue(record.value(), Map.class);
            String paymentId = (String) event.get("paymentId");
            String eventType = (String) event.get("eventType");
            String status = (String) event.get("currentStatus");

            log.info("Processing payment event. paymentId={}, eventType={}, status={}",
                    paymentId, eventType, status);

            webhookDeliveryService.deliver(paymentId, eventType, status, record.value());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment event. key={}", record.key(), e);
            // In production: send to DLQ (Dead Letter Queue)
        }
    }
}
