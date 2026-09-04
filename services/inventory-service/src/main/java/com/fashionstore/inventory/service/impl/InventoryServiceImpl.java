package com.fashionstore.inventory.service.impl;

import com.fashionstore.inventory.dto.inventory.*;
import com.fashionstore.inventory.exception.InventoryErrorCode;
import com.fashionstore.inventory.mapper.InventoryMapper;
import com.fashionstore.inventory.model.Inventory;
import com.fashionstore.inventory.model.InventoryReservation;
import com.fashionstore.inventory.model.InventoryReservationStatus;
import com.fashionstore.inventory.repository.InventoryRepository;
import com.fashionstore.inventory.repository.InventoryReservationRepository;
import com.fashionstore.inventory.service.InventoryService;
import com.fashionstore.common.exception.AppException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InventoryServiceImpl implements InventoryService {

    InventoryRepository inventoryRepository;
    InventoryReservationRepository reservationRepository;
    InventoryMapper inventoryMapper;


    @Override
    public CheckStockResponse checkStock(CheckStockRequest request) {
        log.info("[Inventory] checkStock — {} items", request.getItems().size());

        List<String> variantIds = request.getItems().stream()
                .map(CheckStockRequest.StockItem::getVariantId)
                .toList();

        Map<String, Inventory> inventoryMap = inventoryRepository
                .findByVariantIdIn(variantIds)
                .stream()
                .collect(Collectors.toMap(Inventory::getVariantId, Function.identity()));

        List<CheckStockResponse.StockItemResult> results = new ArrayList<>();
        boolean allAvailable = true;

        for (CheckStockRequest.StockItem item : request.getItems()) {
            Inventory inventory = inventoryMap.get(item.getVariantId());

            if (inventory == null) {
                results.add(CheckStockResponse.StockItemResult.builder()
                        .variantId(item.getVariantId())
                        .available(false)
                        .requestedQty(item.getQuantity())
                        .availableQty(0)
                        .message("Variant không tồn tại trong hệ thống")
                        .build());
                allAvailable = false;
                continue;
            }

            boolean sufficient = inventory.hasEnoughStock(item.getQuantity());
            if (!sufficient) allAvailable = false;

            results.add(CheckStockResponse.StockItemResult.builder()
                    .variantId(item.getVariantId())
                    .available(sufficient)
                    .requestedQty(item.getQuantity())
                    .availableQty(inventory.getQuantityAvailable())
                    .message(sufficient ? "OK"
                            : String.format("Chỉ còn %d sản phẩm, yêu cầu %d",
                            inventory.getQuantityAvailable(), item.getQuantity()))
                    .build());
        }


        return CheckStockResponse.builder()
                .allAvailable(allAvailable)
                .items(results)
                .build();
    }

    @Override
    @Transactional
    public ReserveStockResponse reserveStock(ReserveStockRequest request) {

        log.info("[Inventory] reserveStock — orderId={}, {} items",
                request.getOrderId(), request.getItems().size());

        List<String> reservedVariantIds = new ArrayList<>();
        List<String> failedVariantIds   = new ArrayList<>();

        for (ReserveStockRequest.ReserveItem item : request.getItems()) {

            // Idempotency: đã reserve rồi thì bỏ qua
            boolean alreadyReserved = reservationRepository
                    .existsByOrderIdAndVariantIdAndStatus(
                            request.getOrderId(), item.getVariantId(), InventoryReservationStatus.RESERVED);

            if (alreadyReserved) {
                log.warn("[Inventory] duplicate reserve — orderId={}, variantId={}",
                        request.getOrderId(), item.getVariantId());
                reservedVariantIds.add(item.getVariantId());
                continue;
            }

            // Pessimistic lock để tránh race condition
            Inventory inventory = inventoryRepository
                    .findByVariantIdWithLock(item.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));

            if (!inventory.hasEnoughStock(item.getQuantity())) {
                failedVariantIds.add(item.getVariantId());
                log.warn("[Inventory] insufficient stock — variantId={}, available={}, requested={}",
                        item.getVariantId(), inventory.getQuantityAvailable(), item.getQuantity());
            } else {
                // Tăng reserved
                inventory.setReservedQuantity(
                        inventory.getReservedQuantity() + item.getQuantity());
                inventoryRepository.save(inventory);

                // Lưu reservation record để track và release sau này
                InventoryReservation reservation = InventoryReservation.builder()
                        .orderId(request.getOrderId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .status(InventoryReservationStatus.RESERVED)
                        .build();
                reservationRepository.save(reservation);

                reservedVariantIds.add(item.getVariantId());
            }
        }

        // All-or-nothing: nếu có bất kỳ item nào thất bại → throw để rollback
        if (!failedVariantIds.isEmpty()) {
            log.error("[Inventory] reserve failed — orderId={}, failedVariants={}",
                    request.getOrderId(), failedVariantIds);
            throw new AppException(InventoryErrorCode.STOCK_INSUFFICIENT);
        }

        log.info("[Inventory] reserved successfully — orderId={}, variants={}",
                request.getOrderId(), reservedVariantIds);

        return ReserveStockResponse.builder()
                .success(true)
                .orderId(request.getOrderId())
                .reservedVariantIds(reservedVariantIds)
                .failedVariantIds(List.of())
                .message("Stock reserved successfully")
                .build();
    }

    @Override
    @Transactional
    public void releaseStock(ReleaseStockRequest request) {
        log.info("[Inventory] releaseStock — orderId={}, reason={}",
                request.getOrderId(), request.getReason());

        List<InventoryReservation> reservations = reservationRepository
                .findByOrderIdAndStatus(request.getOrderId(), InventoryReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            // Idempotent — đã release rồi hoặc chưa reserve → bỏ qua
            log.warn("[Inventory] no active reservation found for orderId={} — skip",
                    request.getOrderId());
            return;
        }

        for (InventoryReservation reservation : reservations) {

            // Pessimistic lock
            Inventory inventory = inventoryRepository
                    .findByVariantIdWithLock(reservation.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));

            // Giảm reserved — không bao giờ âm
            int newReserved = Math.max(0,
                    inventory.getReservedQuantity() - reservation.getQuantity());
            inventory.setReservedQuantity(newReserved);
            inventoryRepository.save(inventory);

            // Cập nhật reservation status
            reservation.setStatus(InventoryReservationStatus.RELEASED);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            log.info("[Inventory] released — variantId={}, qty={}, reason={}",
                    reservation.getVariantId(), reservation.getQuantity(), request.getReason());
        }
    }

    @Override
    @Transactional
    public void confirmStock(String orderId) {
        log.info("[Inventory] confirmStock — orderId={}", orderId);

        List<InventoryReservation> reservations = reservationRepository
                .findByOrderIdAndStatus(orderId, InventoryReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.warn("[Inventory] no RESERVED reservations for orderId={}", orderId);
            return;
        }

        for (InventoryReservation reservation : reservations) {

            Inventory inventory = inventoryRepository
                    .findByVariantIdWithLock(reservation.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));

            // Xuất kho: giảm cả onHand lẫn reserved
            inventory.setQuantity(
                    Math.max(0, inventory.getQuantity() - reservation.getQuantity()));
            inventory.setReservedQuantity(
                    Math.max(0, inventory.getReservedQuantity() - reservation.getQuantity()));

            inventoryRepository.save(inventory);

            reservation.setStatus(InventoryReservationStatus.CONFIRMED);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            log.info("[Inventory] confirmed — variantId={}, qty={}",
                    reservation.getVariantId(), reservation.getQuantity());
        }
    }

    @Override
    public InventoryResponse getByVariantId(String variantId) {
        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional
    public void upsertStock(String variantId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        inventoryRepository.findByVariantId(variantId).ifPresentOrElse(
                inventory -> {
                    inventory.setQuantity(quantity);
                    inventoryRepository.save(inventory);
                },
                // TODO(P4/P5): can productId trong ProductVariantStockEvent moi tao duoc dong moi.
                () -> log.warn("[Inventory] upsertStock — chua co ton kho cho variant {}, bo qua", variantId));
    }

    @Override
    @Transactional
    public void deleteStock(String variantId) {
        inventoryRepository.findByVariantId(variantId).ifPresent(inventoryRepository::delete);
    }

    @Override
    public List<InventoryResponse> getByVariantIds(List<String> variantIds) {
        return inventoryRepository.findByVariantIdIn(variantIds)
                .stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }
}
