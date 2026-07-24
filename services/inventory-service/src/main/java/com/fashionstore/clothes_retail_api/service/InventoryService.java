package com.fashionstore.clothes_retail_api.service;

import com.fashionstore.clothes_retail_api.dto.inventory.*;

import java.util.List;

public interface InventoryService {
    /**
     * GET — cart-service dùng để hiển thị tồn kho khi xem giỏ hàng.
     * Trả về quantityAvailable (onHand - reserved).
     * Không có side effect.
     */
    CheckStockResponse checkStock(CheckStockRequest request);

    /**
     * POST — order-service gọi khi tạo order.
     * Tăng quantityReserved, giảm quantityAvailable.
     * Idempotent: cùng orderId + variantId gọi 2 lần → không reserve 2 lần.
     * Nếu bất kỳ item nào không đủ hàng → rollback toàn bộ (all-or-nothing).
     */
    ReserveStockResponse reserveStock(ReserveStockRequest request);

    /**
     * POST — order-service gọi khi cancel order hoặc payment failed.
     * Giảm quantityReserved, tăng quantityAvailable trở lại.
     * Idempotent: gọi 2 lần cho cùng orderId → không release 2 lần.
     */
    void releaseStock(ReleaseStockRequest request);

    /**
     * POST — order-service gọi khi order CONFIRMED (đã thanh toán, hàng xuất kho).
     * Giảm quantityOnHand thực sự + đánh dấu reservation là CONFIRMED.
     */
    void confirmStock(String orderId);

    /** GET — lấy thông tin inventory của 1 variant */
    InventoryResponse getByVariantId(String variantId);

    /** GET — lấy inventory của nhiều variant (batch, dùng trong product-service) */
    List<InventoryResponse> getByVariantIds(List<String> variantIds);

}
