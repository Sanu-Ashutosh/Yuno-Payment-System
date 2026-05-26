package com.yuno.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class WebhookDeliveryService {

    @Value("${webhooks.enabled:true}")
    private boolean webhooksEnabled;

    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    public void deliver(String paymentId, String eventType, String status, String payload) {
        if (!webhooksEnabled) {
            log.debug("Webhooks disabled. Skipping delivery. paymentId={}", paymentId);
            return;
        }
        try {
            // In production: HTTP POST to merchant's webhook URL
            // For assessment: simulate delivery with structured logging
            log.info("WEBHOOK_DELIVERED | paymentId={} | eventType={} | status={} | payload={}",
                    paymentId, eventType, status, payload);
            deliveredCount.incrementAndGet();
        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.error("WEBHOOK_FAILED | paymentId={} | eventType={}", paymentId, eventType, e);
        }
    }

    public long getDeliveredCount() { return deliveredCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
}
