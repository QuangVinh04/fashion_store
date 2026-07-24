package com.fashionstore.clothes_retail_api.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReserveStockResponse {
    private boolean      success;
    private String       orderId;
    private List<String> reservedVariantIds;
    private List<String> failedVariantIds;
    private String       message;
}