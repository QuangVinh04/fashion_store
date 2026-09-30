package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.exception.InventoryErrorCode;
import com.fashionstore.catalog.mapper.InventoryMapper;
import com.fashionstore.catalog.entity.Inventory;
import com.fashionstore.catalog.entity.InventoryReservation;
import com.fashionstore.catalog.entity.InventoryReservationItem;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import com.fashionstore.catalog.entity.enumeration.InventoryStatus;
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

import com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse;
import com.fashionstore.catalog.dto.inventory.LowStockItemResponse;
import com.fashionstore.catalog.dto.inventory.InventoryListItemResponse;
import com.fashionstore.catalog.dto.inventory.InventoryReservationResponse;
import com.fashionstore.catalog.dto.inventory.InventoryStockState;
import com.fashionstore.catalog.dto.inventory.ReceiveStockRequest;
import com.fashionstore.catalog.entity.InventoryLedger;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductVariant;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.entity.enumeration.ProductStatus;
import com.fashionstore.catalog.repository.InventoryLedgerRepository;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.repository.ProductVariantRepository;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    InventoryLedgerRepository inventoryLedgerRepository;
    ProductVariantRepository productVariantRepository;
    ProductRepository productRepository;
    CurrentUserProvider currentUserProvider;
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
        Map<String, Integer> quantities = new TreeMap<>();
        for (InventoryItem item : items) {
            if (item.variantId() == null || item.quantity() <= 0) {
                return null;
            }
            try {
                quantities.merge(item.variantId(), item.quantity(), Math::addExact);
            } catch (ArithmeticException overflow) {
                return null;
            }
        }
        List<InventoryItem> sorted = quantities.entrySet().stream()
                .map(entry -> new InventoryItem(entry.getKey(), entry.getValue())).toList();

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
            int beforeReserved = inv.getReservedQuantity();
            inv.setReservedQuantity(inv.getReservedQuantity() + item.quantity());
            inventoryRepository.save(inv);
            recordLedger(item.variantId(), InventoryLedgerType.RESERVE, item.quantity(), orderId,
                    inv, inv.getQuantity(), beforeReserved, null, null);
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
    public void releaseSaga(ReleaseInventoryCommand command, String correlationId) {
        log.info("[Inventory] saga release — orderId={}, reservationId={}", command.orderId(), command.reservationId());
        String reservationId = releaseInternal(command.orderId(), command.reservationId());
        // Chỉ emit RELEASED nếu thực sự đã release (idempotent: release 2 lần chỉ emit 1 lần)
        if (reservationId != null) {
            InventoryReleasedEvent event = new InventoryReleasedEvent(command.orderId(), reservationId);
            outboxService.saveMessage(
                    command.orderId(),
                    EventTypes.INVENTORY_RELEASED,
                    EventEnvelope.v1(EventTypes.INVENTORY_RELEASED, command.orderId(), correlationId, event));
        }
    }

    private String releaseInternal(String orderId, String expectedReservationId) {
        Optional<InventoryReservation> opt = reservationRepository.findByOrderIdForUpdate(orderId);
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

        List<InventoryReservationItem> items = new ArrayList<>(reservationItemRepository.findByReservationId(reservation.getId()));
        // Lock sorted
        items.sort(Comparator.comparing(InventoryReservationItem::getVariantId));
        for (InventoryReservationItem item : items) {
            Inventory inv = inventoryRepository.findByVariantIdWithLock(item.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
            if (inv.getReservedQuantity() < item.getQuantity()) {
                throw new AppException(InventoryErrorCode.STOCK_INSUFFICIENT);
            }
            int beforeReserved = inv.getReservedQuantity();
            inv.setReservedQuantity(inv.getReservedQuantity() - item.getQuantity());
            inventoryRepository.save(inv);
            recordLedger(item.getVariantId(), InventoryLedgerType.RELEASE, item.getQuantity(), orderId,
                    inv, inv.getQuantity(), beforeReserved, null, null);
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
    public void confirmSaga(ConfirmInventoryCommand command, String correlationId) {
        log.info("[Inventory] saga confirm — orderId={}, reservationId={}", command.orderId(), command.reservationId());
        String reservationId = confirmInternal(command.orderId(), command.reservationId());
        if (reservationId != null) {
            InventoryConfirmedEvent event = new InventoryConfirmedEvent(command.orderId(), reservationId);
            outboxService.saveMessage(
                    command.orderId(),
                    EventTypes.INVENTORY_CONFIRMED,
                    EventEnvelope.v1(EventTypes.INVENTORY_CONFIRMED, command.orderId(), correlationId, event));
        }
    }

    private String confirmInternal(String orderId, String expectedReservationId) {
        Optional<InventoryReservation> opt = reservationRepository.findByOrderIdForUpdate(orderId);
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
        List<InventoryReservationItem> items = new ArrayList<>(reservationItemRepository.findByReservationId(reservation.getId()));
        items.sort(Comparator.comparing(InventoryReservationItem::getVariantId));
        for (InventoryReservationItem item : items) {
            Inventory inv = inventoryRepository.findByVariantIdWithLock(item.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
            if (inv.getQuantity() < item.getQuantity() || inv.getReservedQuantity() < item.getQuantity()) {
                throw new AppException(InventoryErrorCode.STOCK_INSUFFICIENT);
            }
            int beforeQuantity = inv.getQuantity();
            int beforeReserved = inv.getReservedQuantity();
            inv.setQuantity(inv.getQuantity() - item.getQuantity());
            inv.setReservedQuantity(inv.getReservedQuantity() - item.getQuantity());
            inventoryRepository.save(inv);
            recordLedger(item.getVariantId(), InventoryLedgerType.CONFIRM, item.getQuantity(), orderId,
                    inv, beforeQuantity, beforeReserved, null, null);
        }
        reservation.setStatus(InventoryReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("[Inventory] confirmed — orderId={}, reservationId={}", orderId, reservation.getId());
        return reservation.getId();
    }

    @Override
    @Transactional
    public void restock(String orderId) {
        log.info("[Inventory] restock request for orderId={}", orderId);
        Optional<InventoryReservation> opt = reservationRepository.findByOrderIdForUpdate(orderId);
        if (opt.isEmpty()) {
            log.warn("[Inventory] no reservation found for orderId={} — skip restock", orderId);
            return;
        }
        InventoryReservation reservation = opt.get();
        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            log.info("[Inventory] reservation for orderId={} already RELEASED — skip restock", orderId);
            return;
        }
        if (reservation.getStatus() != InventoryReservationStatus.CONFIRMED) {
            log.warn("[Inventory] reservation for orderId={} is {} (not CONFIRMED) — skip restock",
                    orderId, reservation.getStatus());
            return;
        }

        List<InventoryReservationItem> items = new ArrayList<>(reservationItemRepository.findByReservationId(reservation.getId()));
        items.sort(Comparator.comparing(InventoryReservationItem::getVariantId));
        for (InventoryReservationItem item : items) {
            Inventory inv = inventoryRepository.findByVariantIdWithLock(item.getVariantId())
                    .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
            int beforeQuantity = inv.getQuantity();
            try {
                inv.setQuantity(Math.addExact(inv.getQuantity(), item.getQuantity()));
            } catch (ArithmeticException overflow) {
                throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
            }
            inventoryRepository.save(inv);
            recordLedger(item.getVariantId(), InventoryLedgerType.RESTOCK, item.getQuantity(), orderId,
                    inv, beforeQuantity, inv.getReservedQuantity(), null, null);
        }
        reservation.setStatus(InventoryReservationStatus.RELEASED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("[Inventory] restock completed for orderId={}, reservationId={}", orderId, reservation.getId());
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
    public InventoryResponse updateStock(String variantId, UpdateStockRequest request) {
        if (request == null || request.getQuantity() == null || request.getQuantity() < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        String operationId = requiredOperationId(request.getOperationId());
        String reason = requiredReason(request.getReason());
        inventoryLedgerRepository.lockOperationId(operationId);
        Inventory inventory = inventoryRepository.findByVariantIdWithLock(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        Optional<InventoryLedger> previous = inventoryLedgerRepository.findByOperationId(operationId);
        if (previous.isPresent()) {
            InventoryLedger entry = previous.get();
            if (entry.getType() != InventoryLedgerType.ADJUST || !variantId.equals(entry.getVariantId())
                    || !Objects.equals(request.getQuantity(), entry.getQuantityAfter())
                    || !reason.equals(entry.getReason())) {
                throw new AppException(InventoryErrorCode.OPERATION_ID_CONFLICT);
            }
            return inventoryMapper.toResponse(inventory);
        }
        int quantity = request.getQuantity();
        if (quantity < inventory.getReservedQuantity()) {
            throw new AppException(InventoryErrorCode.STOCK_BELOW_RESERVED);
        }
        int diff = quantity - inventory.getQuantity();
        int beforeQuantity = inventory.getQuantity();
        inventory.setQuantity(quantity);
        Inventory saved = inventoryRepository.save(inventory);
        recordLedger(variantId, InventoryLedgerType.ADJUST, diff, null,
                saved, beforeQuantity, saved.getReservedQuantity(), reason, operationId);
        return inventoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InventoryResponse receiveStock(String variantId, ReceiveStockRequest request) {
        if (request == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        String operationId = requiredOperationId(request.getOperationId());
        String reason = requiredReason(request.getReason());
        inventoryLedgerRepository.lockOperationId(operationId);
        Inventory inventory = inventoryRepository.findByVariantIdWithLock(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        Optional<InventoryLedger> previous = inventoryLedgerRepository.findByOperationId(operationId);
        if (previous.isPresent()) {
            InventoryLedger entry = previous.get();
            if (entry.getType() != InventoryLedgerType.IN || !variantId.equals(entry.getVariantId())
                    || !Objects.equals(request.getQuantity(), entry.getQuantity())
                    || !reason.equals(entry.getReason())) {
                throw new AppException(InventoryErrorCode.OPERATION_ID_CONFLICT);
            }
            return inventoryMapper.toResponse(inventory);
        }
        int beforeQuantity = inventory.getQuantity();
        int newQuantity;
        try {
            newQuantity = Math.addExact(beforeQuantity, request.getQuantity());
        } catch (ArithmeticException overflow) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        inventory.setQuantity(newQuantity);
        Inventory saved = inventoryRepository.save(inventory);
        recordLedger(variantId, InventoryLedgerType.IN, request.getQuantity(), null,
                saved, beforeQuantity, saved.getReservedQuantity(), reason, operationId);
        return inventoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InventoryResponse updateThreshold(String variantId, int minThreshold) {
        if (minThreshold < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        Inventory inventory = inventoryRepository.findByVariantIdWithLock(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        inventory.setMinThreshold(minThreshold);
        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Override
    @Transactional
    public void initializeStock(String variantId, int initialQuantity) {
        if (initialQuantity < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        if (initialQuantity == 0) {
            return;
        }
        Inventory inventory = inventoryRepository.findByVariantIdWithLock(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND));
        if (inventory.getQuantity() != 0 || inventory.getReservedQuantity() != 0) {
            throw new AppException(InventoryErrorCode.INITIAL_STOCK_NOT_ALLOWED);
        }
        inventory.setQuantity(initialQuantity);
        inventoryRepository.save(inventory);
        recordLedger(variantId, InventoryLedgerType.IN, initialQuantity, null,
                inventory, 0, 0, "INITIAL_STOCK", null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<InventoryListItemResponse>> searchForAdmin(String query, InventoryStockState state,
                                                                         boolean includeInactive, Pageable pageable) {
        String term = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Inventory> page = inventoryRepository.searchForAdmin(term, state.name(), includeInactive,
                ProductStatus.PUBLISHED, pageable);
        List<String> variantIds = page.getContent().stream().map(Inventory::getVariantId).toList();
        Map<String, ProductVariant> variants = productVariantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<String> productIds = page.getContent().stream().map(Inventory::getProductId).distinct().toList();
        Map<String, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<InventoryListItemResponse> items = page.getContent().stream().map(inv -> {
            ProductVariant variant = variants.get(inv.getVariantId());
            Product product = products.get(inv.getProductId());
            return new InventoryListItemResponse(inv.getVariantId(), inv.getProductId(),
                    product == null ? null : product.getName(), variant == null ? null : variant.getSku(),
                    variant != null && Boolean.TRUE.equals(variant.getActive())
                            && product != null && product.getStatus() == ProductStatus.PUBLISHED,
                    inv.getQuantity(), inv.getReservedQuantity(), inv.getQuantityAvailable(),
                    inv.getMinThreshold(), inv.getUpdatedAt());
        }).toList();
        return pageResponse(page, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<InventoryReservationResponse>> getReservations(String variantId,
            InventoryReservationStatus status, Pageable pageable) {
        if (!inventoryRepository.findByVariantId(variantId).isPresent()) {
            throw new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND);
        }
        Page<InventoryReservationItem> page = reservationItemRepository.findForAdmin(variantId, status, pageable);
        Map<String, InventoryReservation> reservations = reservationRepository.findAllById(page.getContent().stream()
                .map(InventoryReservationItem::getReservationId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryReservation::getId, Function.identity()));
        List<InventoryReservationResponse> items = page.getContent().stream().map(item -> {
            InventoryReservation reservation = reservations.get(item.getReservationId());
            return new InventoryReservationResponse(reservation.getOrderId(), item.getQuantity(),
                    reservation.getStatus(), reservation.getCreatedAt(), reservation.getUpdatedAt());
        }).toList();
        return pageResponse(page, items);
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<InventoryLedgerResponse>> getLedger(String variantId, InventoryLedgerType type,
            LocalDateTime fromTime, LocalDateTime toTime, Pageable pageable) {
        Page<InventoryLedger> page = inventoryLedgerRepository.search(
                variantId == null || variantId.isBlank() ? null : variantId, type, fromTime, toTime, pageable);

        List<InventoryLedgerResponse> items = page.getContent().stream()
                .map(l -> InventoryLedgerResponse.builder()
                        .id(l.getId())
                        .variantId(l.getVariantId())
                        .type(l.getType())
                        .quantity(l.getQuantity())
                        .refOrderId(l.getRefOrderId())
                        .reason(l.getReason())
                        .quantityBefore(l.getQuantityBefore())
                        .quantityAfter(l.getQuantityAfter())
                        .reservedBefore(l.getReservedBefore())
                        .reservedAfter(l.getReservedAfter())
                        .operationId(l.getOperationId())
                        .createdBy(l.getCreatedBy())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();

        return pageResponse(page, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<LowStockItemResponse>> getLowStock(Integer threshold, Pageable pageable) {
        if (threshold != null && threshold < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        Page<Inventory> page = inventoryRepository.findLowStockInventories(threshold, ProductStatus.PUBLISHED, pageable);

        List<String> variantIds = page.getContent().stream().map(Inventory::getVariantId).toList();
        Map<String, ProductVariant> variantMap = productVariantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<String> productIds = page.getContent().stream().map(Inventory::getProductId).distinct().toList();
        Map<String, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<LowStockItemResponse> items = page.getContent().stream().map(inv -> {
            ProductVariant variant = variantMap.get(inv.getVariantId());
            Product product = productMap.get(inv.getProductId());
            String sku = variant != null ? variant.getSku() : null;
            String productName = product != null ? product.getName() : null;

            return LowStockItemResponse.builder()
                    .variantId(inv.getVariantId())
                    .productId(inv.getProductId())
                    .productName(productName)
                    .sku(sku)
                    .quantity(inv.getQuantity())
                    .reservedQuantity(inv.getReservedQuantity())
                    .availableQuantity(inv.getQuantityAvailable())
                    .threshold(threshold == null ? inv.getMinThreshold() : threshold)
                    .build();
        }).toList();

        return pageResponse(page, items);
    }

    @Override
    @Transactional(readOnly = true)
    public long countLowStock(Integer threshold) {
        if (threshold != null && threshold < 0) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        return inventoryRepository.countLowStockInventories(threshold, ProductStatus.PUBLISHED);
    }

    private void recordLedger(String variantId, InventoryLedgerType type, int quantity, String refOrderId,
                              Inventory inventory, int beforeQuantity, int beforeReserved,
                              String reason, String operationId) {
        String createdBy = null;
        try {
            createdBy = currentUserProvider.getCurrentUserId();
        } catch (Exception ignored) {
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "SYSTEM";
        }

        InventoryLedger ledger = InventoryLedger.builder()
                .variantId(variantId)
                .type(type)
                .quantity(quantity)
                .refOrderId(refOrderId)
                .reason(reason)
                .quantityBefore(beforeQuantity)
                .quantityAfter(inventory.getQuantity())
                .reservedBefore(beforeReserved)
                .reservedAfter(inventory.getReservedQuantity())
                .operationId(operationId)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();

        inventoryLedgerRepository.save(ledger);
        log.info("[InventoryLedger] Recorded {} of qty {} for variant {} (order={})",
                type, quantity, variantId, refOrderId);
    }

    private String requiredOperationId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException invalid) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 500) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_QUANTITY);
        }
        return value.trim();
    }

    private <T> PageResponse<List<T>> pageResponse(Page<?> page, List<T> items) {
        return PageResponse.<List<T>>builder()
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalPage(page.getTotalPages())
                .items(items)
                .build();
    }
}
