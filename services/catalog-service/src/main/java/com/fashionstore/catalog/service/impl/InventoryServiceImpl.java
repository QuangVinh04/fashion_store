package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.exception.InventoryErrorCode;
import com.fashionstore.catalog.mapper.InventoryMapper;
import com.fashionstore.catalog.model.Inventory;
import com.fashionstore.catalog.model.InventoryReservation;
import com.fashionstore.catalog.model.InventoryReservationItem;
import com.fashionstore.catalog.model.enumeration.InventoryReservationStatus;
import com.fashionstore.catalog.model.enumeration.InventoryStatus;
import com.fashionstore.catalog.outbox.OutboxService;
import com.fashionstore.catalog.repository.InventoryRepository;
import com.fashionstore.catalog.repository.InventoryReservationItemRepository;
import com.fashionstore.catalog.repository.InventoryReservationRepository;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.InventoryItem;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;
import com.fashionstore.contracts.inventory.event.InventoryConfirmedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReleasedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationFailedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InventoryServiceImpl implements InventoryService {

    InventoryRepository inventoryRepository;
    InventoryReservationRepository reservationRepository;
    InventoryReservationItemRepository reservationItemRepository;
    InventoryMapper inventoryMapper;
    OutboxService outboxService;

    @Override
    @Transactional(readOnly = true)
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
        log.info("[Inventory] HTTP reserveStock — orderId={}, {} items", request.getOrderId(), request.getItems().size());
        List<InventoryItem> items = request.getItems().stream()
                .map(i -> new InventoryItem(i.getVariantId(), i.getQuantity()))
                .toList();
        // HTTP path: không có correlationId, dùng orderId làm aggregate
        String reservationId = reserveInternal(request.getOrderId(), items);
        if (reservationId == null) {
            throw new AppException(InventoryErrorCode.STOCK_INSUFFICIENT);
        }
        return ReserveStockResponse.builder()
                .success(true)
                .orderId(request.getOrderId())
                .reservedVariantIds(items.stream().map(InventoryItem::variantId).toList())
                .failedVariantIds(List.of())
                .message("Stock reserved successfully")
                .build();
    }

    @Override
    @Transactional
    public void reserveSaga(ReservationInventoryCommand command, String correlationId) {
        log.info("[Inventory] saga reserve — orderId={}, items={}", command.orderId(), command.items().size());
        String reservationId = reserveInternal(command.orderId(), command.items());
        if (reservationId == null) {
            InventoryReservationFailedEvent failed =
                    new InventoryReservationFailedEvent(command.orderId(), "STOCK_INSUFFICIENT", "Insufficient stock");
            outboxService.saveMessage(
                    command.orderId(),
                    EventTypes.INVENTORY_REJECTED,
                    EventEnvelope.v1(EventTypes.INVENTORY_REJECTED, command.orderId(), correlationId, failed));
            return;
        }
        InventoryReservationEvent success = new InventoryReservationEvent(command.orderId(), reservationId);
        outboxService.saveMessage(
                command.orderId(),
                EventTypes.INVENTORY_RESERVED,
                EventEnvelope.v1(EventTypes.INVENTORY_RESERVED, command.orderId(), correlationId, success));
    }

    /**
     * All-or-nothing: lock sorted → check all → mutate all. Idempotent theo orderId.
     * Return reservationId nếu thành công, null nếu thiếu hàng.
     */
    private String reserveInternal(String orderId, List<InventoryItem> items) {
        // Idempotent: đã reserve rồi thì trả lại id cũ
        Optional<InventoryReservation> existing = reservationRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == InventoryReservationStatus.RESERVED) {
                log.warn("[Inventory] duplicate reserve — orderId={} already RESERVED", orderId);
                return existing.get().getId();
            }
            // Đã ở trạng thái terminal (CONFIRMED/RELEASED/REJECTED) → không reserve lại
            log.warn("[Inventory] duplicate reserve — orderId={} already {}", orderId, existing.get().getStatus());
            return null;
        }

        // Sort để tránh deadlock khi 2 đơn lock cùng tập variant theo thứ tự khác nhau
        List<InventoryItem> sorted = items.stream()
                .sorted(Comparator.comparing(InventoryItem::variantId))
                .toList();

        // Phase 1: lock all + check
        Map<String, Inventory> locked = new LinkedHashMap<>();
        for (InventoryItem item : sorted) {
            Inventory inv = inventoryRepository.findByVariantIdWithLock(item.variantId())
                    .orElse(null);
            if (inv == null || !inv.hasEnoughStock(item.quantity())) {
                log.warn("[Inventory] insufficient — variantId={}, available={}, requested={}",
                        item.variantId(),
                        inv == null ? 0 : inv.getQuantityAvailable(),
                        item.quantity());
                return null;
            }
            locked.put(item.variantId(), inv);
        }

        // Phase 2: mutate reserved
        for (InventoryItem item : sorted) {
            Inventory inv = locked.get(item.variantId());
            inv.setReservedQuantity(inv.getReservedQuantity() + item.quantity());
            inventoryRepository.save(inv);
        }

        InventoryReservation reservation = InventoryReservation.builder()
                .orderId(orderId)
                .status(InventoryReservationStatus.RESERVED)
                .build();
        reservation = reservationRepository.save(reservation);

        for (InventoryItem item : sorted) {
            InventoryReservationItem row = InventoryReservationItem.builder()
                    .reservationId(reservation.getId())
                    .variantId(item.variantId())
                    .quantity(item.quantity())
                    .build();
            reservationItemRepository.save(row);
        }
        return reservation.getId();
    }

    @Override
    @Transactional
    public void releaseStock(ReleaseStockRequest request) {
        releaseInternal(request.getOrderId(), null);
    }

    @Override
    @Transactional
    public void releaseSaga(ReleaseInventoryCommand command) {
        log.info("[Inventory] saga release — orderId={}, reservationId={}", command.orderId(), command.reservationId());
        String reservationId = releaseInternal(command.orderId(), command.reservationId());
        // Chỉ emit RELEASED nếu thực sự đã release (idempotent: release 2 lần chỉ emit 1 lần)
        if (reservationId != null) {
            InventoryReleasedEvent event = new InventoryReleasedEvent(command.orderId(), reservationId);
            // correlationId không có trong ReleaseInventoryCommand, dùng orderId
            outboxService.saveMessage(
                    command.orderId(),
                    EventTypes.INVENTORY_RELEASED,
                    EventEnvelope.v1(EventTypes.INVENTORY_RELEASED, command.orderId(), command.orderId(), event));
        }
    }

    private String releaseInternal(String orderId, String expectedReservationId) {
        Optional<InventoryReservation> opt = reservationRepository.findByOrderId(orderId);
        if (opt.isEmpty()) {
            log.warn("[Inventory] no reservation for orderId={} — skip release", orderId);
            return null;
        }
        InventoryReservation reservation = opt.get();
        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            log.warn("[Inventory] reservation orderId={} status={} — skip release", orderId, reservation.getStatus());
            return null;
        }
        if (expectedReservationId != null && !expectedReservationId.equals(reservation.getId())) {
            log.warn("[Inventory] reservationId mismatch orderId={} expected={} actual={}",
                    orderId, expectedReservationId, reservation.getId());
            return null;
        }

        List<InventoryReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        // Lock sorted
        items.sort(Comparator.comparing(InventoryReservationItem::getVariantId));
        for (InventoryReservationItem item : items) {
            Inventory inv = inventoryRepository.findByVariantIdWithLock(item.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
            inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - item.getQuantity()));
            inventoryRepository.save(inv);
        }
        reservation.setStatus(InventoryReservationStatus.RELEASED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("[Inventory] released — orderId={}, reservationId={}", orderId, reservation.getId());
        return reservation.getId();
    }

    @Override
    @Transactional
    public void confirmStock(String orderId) {
        confirmInternal(orderId, null);
    }

    @Override
    @Transactional
    public void confirmSaga(ConfirmInventoryCommand command) {
        log.info("[Inventory] saga confirm — orderId={}, reservationId={}", command.orderId(), command.reservationId());
        String reservationId = confirmInternal(command.orderId(), command.reservationId());
        if (reservationId != null) {
            InventoryConfirmedEvent event = new InventoryConfirmedEvent(command.orderId(), reservationId);
            outboxService.saveMessage(
                    command.orderId(),
                    EventTypes.INVENTORY_CONFIRMED,
                    EventEnvelope.v1(EventTypes.INVENTORY_CONFIRMED, command.orderId(), command.orderId(), event));
        }
    }

    private String confirmInternal(String orderId, String expectedReservationId) {
        Optional<InventoryReservation> opt = reservationRepository.findByOrderId(orderId);
        if (opt.isEmpty()) {
            log.warn("[Inventory] no reservation for orderId={} — skip confirm", orderId);
            return null;
        }
        InventoryReservation reservation = opt.get();
        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            log.warn("[Inventory] reservation orderId={} status={} — skip confirm", orderId, reservation.getStatus());
            return null;
        }
        if (expectedReservationId != null && !expectedReservationId.equals(reservation.getId())) {
            log.warn("[Inventory] reservationId mismatch orderId={} expected={} actual={}",
                    orderId, expectedReservationId, reservation.getId());
            return null;
        }
        List<InventoryReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        items.sort(Comparator.comparing(InventoryReservationItem::getVariantId));
        for (InventoryReservationItem item : items) {
            Inventory inv = inventoryRepository.findByVariantIdWithLock(item.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
            inv.setQuantity(Math.max(0, inv.getQuantity() - item.getQuantity()));
            inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - item.getQuantity()));
            inventoryRepository.save(inv);
        }
        reservation.setStatus(InventoryReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("[Inventory] confirmed — orderId={}, reservationId={}", orderId, reservation.getId());
        return reservation.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getByVariantId(String variantId) {
        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> getByVariantIds(List<String> variantIds) {
        return inventoryRepository.findByVariantIdIn(variantIds)
                .stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public InventoryResponse updateStock(String variantId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        Inventory inventory = inventoryRepository.findByVariantIdWithLock(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        if (quantity < inventory.getReservedQuantity()) {
            throw new AppException(InventoryErrorCode.STOCK_BELOW_RESERVED);
        }
        inventory.setQuantity(quantity);
        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Override
    @Transactional
    public void ensureStock(String variantId, String productId) {
        inventoryRepository.findByVariantId(variantId).ifPresentOrElse(
                inventory -> { },
                () -> {
                    Inventory created = Inventory.builder()
                            .variantId(variantId)
                            .productId(productId)
                            .quantity(0)
                            .reservedQuantity(0)
                            .status(InventoryStatus.ACTIVE)
                            .build();
                    inventoryRepository.save(created);
                });
    }

    @Override
    @Transactional
    public void deleteStock(String variantId) {
        inventoryRepository.findByVariantId(variantId).ifPresent(inventoryRepository::delete);
    }
}
