package com.fashionstore.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record RefundWalletRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(max = 36) String returnId,
        @NotBlank @Size(max = 36) String orderId,
        @NotBlank @Size(max = 500) String reason,
        @Pattern(regexp = "RETURN_REFUND|VERIFIED_LOSS|CARRIER_RETURN") String reasonType
) {
    public RefundWalletRequest(BigDecimal amount, String returnId, String orderId, String reason) {
        this(amount, returnId, orderId, reason, "RETURN_REFUND");
    }
}
