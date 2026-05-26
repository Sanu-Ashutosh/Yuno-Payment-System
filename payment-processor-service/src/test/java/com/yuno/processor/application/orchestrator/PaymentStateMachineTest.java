package com.yuno.processor.application.orchestrator;

import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.exception.InvalidPaymentStateException;
import com.yuno.processor.domain.statemachine.PaymentStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() { stateMachine = new PaymentStateMachine(); }

    @Test
    void validTransitions_shouldNotThrow() {
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.INITIATED, PaymentStatus.PROCESSING));
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.ROUTING));
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.ROUTING, PaymentStatus.PROVIDER_CALLED));
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.PROVIDER_CALLED, PaymentStatus.SUCCESS));
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.PROVIDER_CALLED, PaymentStatus.RETRYING));
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.RETRYING, PaymentStatus.FAILOVER_TRIGGERED));
    }

    @Test
    void invalidTransitions_shouldThrow() {
        assertThatThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.SUCCESS, PaymentStatus.PROCESSING))
                .isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.INITIATED, PaymentStatus.SUCCESS))
                .isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() ->
                stateMachine.validateTransition(PaymentStatus.PERMANENTLY_FAILED, PaymentStatus.RETRYING))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void terminalStates_areCorrect() {
        assertThat(stateMachine.isTerminal(PaymentStatus.SUCCESS)).isTrue();
        assertThat(stateMachine.isTerminal(PaymentStatus.FAILED)).isTrue();
        assertThat(stateMachine.isTerminal(PaymentStatus.PERMANENTLY_FAILED)).isTrue();
        assertThat(stateMachine.isTerminal(PaymentStatus.PROCESSING)).isFalse();
        assertThat(stateMachine.isTerminal(PaymentStatus.RETRYING)).isFalse();
    }
}
