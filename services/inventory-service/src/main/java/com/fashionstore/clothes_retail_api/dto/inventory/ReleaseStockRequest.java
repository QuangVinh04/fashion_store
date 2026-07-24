package com.fashionstore.clothes_retail_api.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReleaseStockRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    // Lý do release — dùng cho audit log
    private String reason; // CANCELLED_BY_USER, PAYMENT_FAILED, EXPIRED, ADMIN_CANCEL
}
