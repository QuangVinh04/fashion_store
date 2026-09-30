package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;

import java.util.List;
import java.time.LocalDateTime;
import com.fashionstore.catalog.dto.inventory.*;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import com.fashionstore.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface InventoryService {
    CheckStockResponse checkStock(CheckStockRequest request);
    ReserveStockResponse reserveStock(ReserveStockRequest request);
    void releaseStock(ReleaseStockRequest request);
    void confirmStock(String orderId);
    void restock(String orderId);
    InventoryResponse getByVariantId(String variantId);
    List<InventoryResponse> getByVariantIds(List<String> variantIds);
    InventoryResponse updateStock(String variantId, UpdateStockRequest request);
    InventoryResponse receiveStock(String variantId, ReceiveStockRequest request);
    InventoryResponse updateThreshold(String variantId, int minThreshold);
    void initializeStock(String variantId, int initialQuantity);
    PageResponse<List<InventoryListItemResponse>> searchForAdmin(String query, InventoryStockState state,
                                                                  boolean includeInactive, Pageable pageable);
    PageResponse<List<InventoryReservationResponse>> getReservations(String variantId,
                                                                       InventoryReservationStatus status, Pageable pageable);
    void ensureStock(String variantId, String productId);
    void deleteStock(String variantId);

    // Saga (RabbitMQ) — all-or-nothing, idempotent theo orderId
    void reserveSaga(ReservationInventoryCommand command, String correlationId);
    void confirmSaga(ConfirmInventoryCommand command, String correlationId);
    void releaseSaga(ReleaseInventoryCommand command, String correlationId);

    com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse>> getLedger(
            String variantId, InventoryLedgerType type, LocalDateTime fromTime,
            LocalDateTime toTime, Pageable pageable
    );

    com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.dto.inventory.LowStockItemResponse>> getLowStock(
            Integer threshold,
            Pageable pageable
    );

    long countLowStock(Integer threshold);
}
