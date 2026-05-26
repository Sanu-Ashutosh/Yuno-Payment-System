package com.yuno.common.exception;

import com.yuno.common.enums.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(PaymentStatus from, PaymentStatus to) {
        super("Invalid state transition from " + from + " to " + to);
    }
}
