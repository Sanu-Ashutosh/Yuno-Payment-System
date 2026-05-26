package com.yuno.common.dto;

import com.yuno.common.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
@Jacksonized
public class CreatePaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private final BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-character ISO code")
    private final String currency;

    @NotNull(message = "Payment method is required")
    private final PaymentMethod paymentMethod;

    @NotBlank(message = "Customer ID is required")
    private final String customerId;

    private final Map<String, String> metadata;
}
