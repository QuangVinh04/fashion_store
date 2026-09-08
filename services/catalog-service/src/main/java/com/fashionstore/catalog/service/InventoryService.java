package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;

import java.util.List;

public interface InventoryService {
    CheckStockResponse checkStock(CheckStockRequest request);
    ReserveStockResponse reserveStock(ReserveStockRequest request);
    void releaseStock(ReleaseStockRequest request);
    void confirmStock(String orderId);
    InventoryResponse getByVariantId(String variantId);
    List<InventoryResponse> getByVariantIds(List<String> variantIds);
    void upsertStock(String variantId, Integer quantity);
    void upsertStock(String variantId, String productId, Integer quantity);
    void deleteStock(String variantId);

    // Saga (RabbitMQ) — all-or-nothing, idempotent theo orderId
    void reserveSaga(ReservationInventoryCommand command, String correlationId);
    void confirmSaga(ConfirmInventoryCommand command);
    void releaseSaga(ReleaseInventoryCommand command);
}
