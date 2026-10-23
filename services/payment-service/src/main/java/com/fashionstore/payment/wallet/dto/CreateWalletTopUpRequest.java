package com.fashionstore.payment.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateWalletTopUpRequest(@NotNull @Positive BigDecimal amount,@NotBlank @Size(max=160) String operationId) {
    public CreateWalletTopUpRequest(long amount,String operationId) { this(BigDecimal.valueOf(amount),operationId); }
}
