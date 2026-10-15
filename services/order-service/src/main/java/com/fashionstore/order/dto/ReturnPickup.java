package com.fashionstore.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bản chụp những gì cần để tạo đơn trả hàng bên GHN (lấy tại khách, giao về kho). Dựng trong
 * transaction rồi mới gọi GHN ngoài transaction, nên không mang entity lazy.
 */
public record ReturnPickup(
        String returnId,
        String orderCode,
        String senderName,
        String senderPhone,
        String senderAddress,
        String senderWard,
        String senderProvince,
        int weightGram,
        List<Item> items) {

    public record Item(String name, String code, int quantity, BigDecimal price) {}
}
