package com.yuno.processor.domain.statemachine;

import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.exception.InvalidPaymentStateException;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.INITIATED,
                EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PROCESSING,
                EnumSet.of(PaymentStatus.ROUTING, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.ROUTING,
                EnumSet.of(PaymentStatus.PROVIDER_CALLED, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PROVIDER_CALLED,
                EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED,
                        PaymentStatus.RETRYING, PaymentStatus.FAILOVER_TRIGGERED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.RETRYING,
                EnumSet.of(PaymentStatus.PROVIDER_CALLED, PaymentStatus.FAILOVER_TRIGGERED,
                        PaymentStatus.PERMANENTLY_FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILOVER_TRIGGERED,
                EnumSet.of(PaymentStatus.PROVIDER_CALLED, PaymentStatus.PERMANENTLY_FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.SUCCESS, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PERMANENTLY_FAILED, EnumSet.noneOf(PaymentStatus.class));
    }

    public void validateTransition(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidPaymentStateException(from, to);
        }
    }

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to);
    }

    public boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.PERMANENTLY_FAILED;
    }
}
