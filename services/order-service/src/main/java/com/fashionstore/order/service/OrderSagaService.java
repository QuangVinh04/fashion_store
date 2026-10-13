package com.fashionstore.order.service;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.inventory.event.InventoryConfirmationRejectedEvent;
import com.fashionstore.contracts.inventory.event.InventoryConfirmedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReleasedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationFailedEvent;
import com.fashionstore.contracts.order.OrderCancelledEvent;
import com.fashionstore.contracts.order.OrderConfirmedEvent;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.contracts.payment.command.RefundWalletSagaCommand;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentCancellationRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentCancelledEvent;
import com.fashionstore.contracts.payment.event.PaymentFailedEvent;
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.contracts.payment.event.WalletSagaCompensatedEvent;
import com.fashionstore.contracts.payment.event.WalletSagaCompensationRejectedEvent;
import com.fashionstore.order.config.SagaConsumers;
import com.fashionstore.order.dto.SagaCommand;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.enumeration.CartStatus;
import com.fashionstore.order.entity.enumeration.OrderSagaStatus;
import com.fashionstore.order.entity.enumeration.OrderSagaStep;
import com.fashionstore.order.entity.enumeration.OrderSagaType;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.event.SagaOutbox;
import com.fashionstore.order.repository.CartRepository;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Điều phối Saga đặt hàng, viết trực tiếp từng bước để đọc và debug luồng chạy.
 *
 * <p>Luồng tiến: giữ kho → yêu cầu thanh toán → chốt kho → xác nhận đơn. Luồng bù trừ: hủy/hoàn
 * tiền → nhả kho → hủy đơn (tùy tài nguyên đã giữ). Mỗi handler xử lý một event trong một
 * transaction; event tiếp theo chạy ở transaction khác. Ghi outbox chỉ lưu lệnh cần gửi; relay gửi
 * RabbitMQ sau khi transaction commit.
 *
 * <p>Cố ý lặp lại guard, chuyển trạng thái và payload để không phải nhảy qua helper. Khi chuyển
 * bước luôn reset retryCount, đặt deadline và updatedAt. Timeout/retry được xử lý riêng trong
 * OrderSagaTimeoutScanner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaService {
    private final ProcessedMessageService processedMessageService;
    private final OrderSagaRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final SagaOutbox sagaOutbox;
    private final ObjectMapper objectMapper;
    private final CartRepository cartRepository;
    private final PromotionService promotionService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderNotificationService orderNotificationService;

    // =========================================================================
    // PHẦN 1: LUỒNG THÀNH CÔNG (GIỮ KHO → THANH TOÁN → CHỐT KHO → XÁC NHẬN ĐƠN)
    // =========================================================================

    /** BƯỚC 1: RESERVE_INVENTORY → AUTHORIZE_PAYMENT; nhả reservation mồ côi nếu event về muộn. */
    @Transactional
    public void inventoryReserved(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_RESERVED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    InventoryReservationEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), InventoryReservationEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    // Reservation về muộn: nhả kho mồ côi, giữ nguyên trạng thái kết thúc.
                    if (saga.isTerminal()) {
                        // Xử lý giữ kho muộn khi Saga đã kết thúc
                        if (saga.getInventoryReservationId() != null) {
                            // Lưu Saga và command trong cùng transaction.
                            sagaRepository.save(saga);
                            return;
                        }
                        saga.setInventoryReservationId(event.reservationId());
                        saga.setUpdatedAt(LocalDateTime.now());
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.INVENTORY_RELEASE_REQUESTED,
                                        new ReleaseInventoryCommand(
                                                saga.getOrderId(), event.reservationId())));
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.RESERVE_INVENTORY) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    // [1.1] Lưu ID giữ kho và chuyển bước sang AUTHORIZE_PAYMENT
                    LocalDateTime now = LocalDateTime.now();
                    saga.setInventoryReservationId(event.reservationId());
                    saga.setCurrentStep(OrderSagaStep.AUTHORIZE_PAYMENT);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.AUTHORIZE_PAYMENT.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.AUTHORIZE_PAYMENT.timeout()));
                    saga.setUpdatedAt(now);

                    // [1.2] Phát lệnh yêu cầu thanh toán kèm deadline của bước
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.PAYMENT_REQUESTED,
                                    new AuthorizePaymentCommand(
                                            order.getId(),
                                            order.getUserId(),
                                            order.getPaymentMethod().name(),
                                            order.getPaymentProvider().name(),
                                            order.getTotalAmount(),
                                            order.getCurrency(),
                                            order.getClientIp(),
                                            saga.getStepDeadline()
                                                    .atZone(ZoneId.systemDefault())
                                                    .toInstant())));
                    return;
                });
    }

    /** BƯỚC 1b: Lưu link/QR; vẫn chờ thanh toán ở AUTHORIZE_PAYMENT. */
    @Transactional
    public void paymentInitiated(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_INITIATED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    PaymentInitiatedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), PaymentInitiatedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.AUTHORIZE_PAYMENT) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.

                    order.setPaymentUrl(event.paymentUrl());
                    orderRepository.save(order);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    return;
                });
    }

    /** BƯỚC 2: AUTHORIZE_PAYMENT → CONFIRM_INVENTORY; phục hồi tiền về muộn ngay trong handler. */
    @Transactional
    public void paymentCompleted(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_COMPLETED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    PaymentSuccessEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), PaymentSuccessEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    // Nhận tiền muộn khi Saga đã đóng hoặc đang hủy thanh toán/nhả kho.
                    if (saga.isTerminal()
                            || saga.getCurrentStep() == OrderSagaStep.CANCEL_PAYMENT
                            || saga.getCurrentStep() == OrderSagaStep.RELEASE_INVENTORY) {
                        boolean stockReleased = saga.getStatus() == OrderSagaStatus.COMPENSATED;

                        if (order.getPaymentMethod() != PaymentMethod.ONLINE
                                || saga.getStatus() == OrderSagaStatus.COMPLETED) {
                            // Lưu Saga và command trong cùng transaction.
                            sagaRepository.save(saga);
                            return;
                        }
                        if (event.paymentId() == null
                                || event.paymentId().isBlank()
                                || saga.getPaymentId() != null
                                        && !saga.getPaymentId().equals(event.paymentId())) {
                            orderStatusHistoryRepository.save(
                                    OrderStatusHistory.builder()
                                            .order(order)
                                            .fromStatus(order.getStatus())
                                            .toStatus(order.getStatus())
                                            .action("LATE_PAYMENT_REVIEW_REQUIRED")
                                            .changedBy("PAYMENT_SERVICE")
                                            .reason(
                                                    "Late capture payment identity does not match"
                                                            + " the checkout saga")
                                            .build());
                            // Lưu Saga và command trong cùng transaction.
                            sagaRepository.save(saga);
                            return;
                        }
                        saga.setPaymentId(event.paymentId());
                        order.setPaymentId(event.paymentId());
                        orderRepository.save(order);
                        if (order.getStatus() != OrderStatus.PENDING
                                        && order.getStatus() != OrderStatus.CANCELLED
                                || order.getShipmentInitiationToken() != null
                                || order.getTrackingCode() != null
                                        && !order.getTrackingCode().isBlank()
                                || saga.getInventoryReservationId() == null
                                || saga.getStatus() == OrderSagaStatus.FAILED) {
                            orderStatusHistoryRepository.save(
                                    OrderStatusHistory.builder()
                                            .order(order)
                                            .fromStatus(order.getStatus())
                                            .toStatus(order.getStatus())
                                            .action("LATE_PAYMENT_REVIEW_REQUIRED")
                                            .changedBy("PAYMENT_SERVICE")
                                            .reason(
                                                    "Verified late funds retained; fulfillment and"
                                                            + " inventory disposition need"
                                                            + " verification")
                                            .build());
                            // Lưu Saga và command trong cùng transaction.
                            sagaRepository.save(saga);
                            return;
                        }
                        if (stockReleased) {
                            LocalDateTime now = LocalDateTime.now();
                            saga.setStatus(OrderSagaStatus.COMPENSATING);
                            saga.setFailureCode("LATE_PAYMENT_STOCK_RELEASED");
                            saga.setFailureReason(
                                    "Late online capture after verified inventory release");
                            saga.setCurrentStep(OrderSagaStep.REFUND_ORDER_FUNDS);
                            saga.setRetryCount(0);
                            saga.setStepDeadline(
                                    OrderSagaStep.REFUND_ORDER_FUNDS.timeout() == null
                                            ? null
                                            : now.plus(OrderSagaStep.REFUND_ORDER_FUNDS.timeout()));
                            saga.setUpdatedAt(now);
                            // Lưu Saga và command trong cùng transaction.
                            sagaRepository.save(saga);
                            sagaOutbox.emit(
                                    saga,
                                    SagaCommand.of(
                                            EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                            new RefundOrderFundsCommand(
                                                    "saga-online:" + saga.getPaymentId(),
                                                    order.getId(),
                                                    saga.getPaymentId(),
                                                    order.getTotalAmount(),
                                                    order.getCurrency(),
                                                    "SAGA_COMPENSATION",
                                                    saga.getId())));
                            return;
                        }
                        LocalDateTime now = LocalDateTime.now();
                        saga.setStatus(OrderSagaStatus.COMPENSATING);
                        saga.setFailureCode("LATE_PAYMENT_STOCK_PENDING");
                        saga.setFailureReason(
                                "Late online capture; refund waits for inventory release"
                                        + " acknowledgement");
                        saga.setCurrentStep(OrderSagaStep.RELEASE_INVENTORY);
                        saga.setRetryCount(0);
                        saga.setStepDeadline(
                                OrderSagaStep.RELEASE_INVENTORY.timeout() == null
                                        ? null
                                        : now.plus(OrderSagaStep.RELEASE_INVENTORY.timeout()));
                        saga.setUpdatedAt(now);
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.INVENTORY_RELEASE_REQUESTED,
                                        new ReleaseInventoryCommand(
                                                saga.getOrderId(),
                                                saga.getInventoryReservationId())));
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.AUTHORIZE_PAYMENT) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    // [2.1] Ghi nhận mã thanh toán và chuyển bước sang CONFIRM_INVENTORY
                    LocalDateTime now = LocalDateTime.now();
                    saga.setPaymentId(event.paymentId());
                    saga.setCurrentStep(OrderSagaStep.CONFIRM_INVENTORY);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.CONFIRM_INVENTORY.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.CONFIRM_INVENTORY.timeout()));
                    saga.setUpdatedAt(now);

                    // [2.2] Phát lệnh chốt kho
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                                    new ConfirmInventoryCommand(
                                            saga.getOrderId(), saga.getInventoryReservationId())));
                    return;
                });
    }

    /** BƯỚC 3: CONFIRM_INVENTORY → DONE; xác nhận đơn, coupon, lịch sử, giỏ hàng và thông báo. */
    @Transactional
    public void inventoryConfirmed(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_CONFIRMED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    InventoryConfirmedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), InventoryConfirmedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.CONFIRM_INVENTORY) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    // [3.1] Saga hoàn tất thành công
                    LocalDateTime now = LocalDateTime.now();
                    saga.setStatus(OrderSagaStatus.COMPLETED);
                    saga.setFailureCode(null);
                    saga.setFailureReason(null);
                    saga.setCurrentStep(OrderSagaStep.DONE);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(null);
                    saga.setUpdatedAt(now);
                    saga.setCompletedAt(now);

                    // [3.2] Cập nhật đơn hàng sang CONFIRMED

                    order.confirm(saga.getPaymentId());
                    orderRepository.save(order);
                    promotionService.confirm(order.getId());

                    // [3.3] Ghi lịch sử trạng thái
                    orderStatusHistoryRepository.save(
                            OrderStatusHistory.builder()
                                    .order(order)
                                    .fromStatus(OrderStatus.PENDING)
                                    .toStatus(OrderStatus.CONFIRMED)
                                    .action("ORDER_CONFIRMED")
                                    .changedBy("SYSTEM")
                                    .reason("Saga hoàn tất thanh toán và giữ kho thành công")
                                    .build());

                    // [3.4] Xóa sản phẩm tương ứng khỏi giỏ hàng
                    // Xóa đúng số lượng sản phẩm đã đặt khỏi giỏ hàng.
                    Cart cart =
                            cartRepository
                                    .findByUserIdAndStatus(order.getUserId(), CartStatus.ACTIVE)
                                    .orElse(null);
                    if (cart != null) {

                        Map<String, Integer> orderedQuantities =
                                order.getItems().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        OrderItem::getVariantId,
                                                        OrderItem::getQuantity,
                                                        Integer::sum));

                        cart.getItems()
                                .removeIf(
                                        item -> {
                                            Integer ordered =
                                                    orderedQuantities.get(item.getVariantId());
                                            if (ordered == null) {
                                                return false;
                                            }
                                            if (item.getQuantity() <= ordered) {
                                                return true;
                                            }
                                            item.setQuantity(item.getQuantity() - ordered);
                                            return false;
                                        });
                        cartRepository.save(cart);
                    }

                    // [3.5] Gửi thông báo cho khách hàng
                    orderNotificationService.sendOrderConfirmedNotification(order);

                    // [3.6] Phát sự kiện xác nhận đơn hàng
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.ORDER_CONFIRMED,
                                    new OrderConfirmedEvent(
                                            order.getId(),
                                            order.getUserId(),
                                            order.getCheckoutId())));
                    return;
                });
    }

    // =========================================================================
    // PHẦN 2: BÙ TRỪ VÀ PHỤC HỒI (HỦY/HOÀN TIỀN → NHẢ KHO → HỦY ĐƠN)
    // =========================================================================

    /** BÙ TRỪ: Không giữ được kho; đóng Saga và hủy đơn. */
    @Transactional
    public void inventoryRejected(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_REJECTED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    InventoryReservationFailedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), InventoryReservationFailedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.RESERVE_INVENTORY) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    // Đóng saga dạng bù trừ (vì không có tài nguyên nào cần thu hồi)
                    LocalDateTime now = LocalDateTime.now();
                    saga.setStatus(OrderSagaStatus.COMPENSATED);
                    saga.setFailureCode(event.failureCode());
                    saga.setFailureReason(event.failureMessage());
                    saga.setCurrentStep(OrderSagaStep.DONE);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(null);
                    saga.setUpdatedAt(now);
                    saga.setCompletedAt(now);

                    if (order.getStatus() == OrderStatus.CANCELLED) {
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        return;
                    }
                    order.cancel(event.failureMessage());
                    orderRepository.save(order);
                    promotionService.release(order.getId());
                    orderStatusHistoryRepository.save(
                            OrderStatusHistory.builder()
                                    .order(order)
                                    .fromStatus(OrderStatus.PENDING)
                                    .toStatus(OrderStatus.CANCELLED)
                                    .action("ORDER_CANCELLED")
                                    .changedBy("SYSTEM")
                                    .reason(event.failureMessage())
                                    .build());
                    orderNotificationService.sendOrderCancelledNotification(
                            order, event.failureMessage());
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.ORDER_CANCELLED,
                                    new OrderCancelledEvent(
                                            order.getId(),
                                            order.getUserId(),
                                            event.failureMessage())));
                    return;
                });
    }

    /** BÙ TRỪ: Thanh toán lỗi; chuyển sang RELEASE_INVENTORY. */
    @Transactional
    public void paymentFailed(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_FAILED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    PaymentFailedEvent event =
                            objectMapper.convertValue(envelope.payload(), PaymentFailedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.AUTHORIZE_PAYMENT) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    LocalDateTime now = LocalDateTime.now();
                    saga.setStatus(OrderSagaStatus.COMPENSATING);
                    saga.setFailureCode("PAYMENT_FAILED");
                    saga.setFailureReason(event.reason());
                    saga.setCurrentStep(OrderSagaStep.RELEASE_INVENTORY);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.RELEASE_INVENTORY.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.RELEASE_INVENTORY.timeout()));
                    saga.setUpdatedAt(now);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.INVENTORY_RELEASE_REQUESTED,
                                    new ReleaseInventoryCommand(
                                            saga.getOrderId(), saga.getInventoryReservationId())));
                    return;
                });
    }

    /** BÙ TRỪ: Hủy thanh toán thành công; chuyển sang RELEASE_INVENTORY. */
    @Transactional
    public void paymentCancelled(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_CANCELLED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    PaymentCancelledEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), PaymentCancelledEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.CANCEL_PAYMENT) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    LocalDateTime now = LocalDateTime.now();
                    saga.setCurrentStep(OrderSagaStep.RELEASE_INVENTORY);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.RELEASE_INVENTORY.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.RELEASE_INVENTORY.timeout()));
                    saga.setUpdatedAt(now);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.INVENTORY_RELEASE_REQUESTED,
                                    new ReleaseInventoryCommand(
                                            saga.getOrderId(), saga.getInventoryReservationId())));
                    return;
                });
    }

    /** PHỤC HỒI TIẾN: Tiền đã thu nên tiếp tục CONFIRM_INVENTORY. */
    @Transactional
    public void paymentCancellationRejected(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_CANCELLATION_REJECTED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    PaymentCancellationRejectedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), PaymentCancellationRejectedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.CANCEL_PAYMENT) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    LocalDateTime now = LocalDateTime.now();
                    saga.setStatus(OrderSagaStatus.RUNNING);
                    saga.setFailureCode(null);
                    saga.setFailureReason(null);
                    saga.setCurrentStep(OrderSagaStep.CONFIRM_INVENTORY);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.CONFIRM_INVENTORY.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.CONFIRM_INVENTORY.timeout()));
                    saga.setUpdatedAt(now);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                                    new ConfirmInventoryCommand(
                                            saga.getOrderId(), saga.getInventoryReservationId())));
                    return;
                });
    }

    /** BÙ TRỪ: Chốt kho bị từ chối; chọn hoàn ví, hoàn tiền online hoặc nhả kho COD. */
    @Transactional
    public void inventoryConfirmationRejected(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                "order.inventory-confirmation-rejected.v1",
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    InventoryConfirmationRejectedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), InventoryConfirmationRejectedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.CONFIRM_INVENTORY) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    if (!Objects.equals(saga.getInventoryReservationId(), event.reservationId())
                            || (!"RESERVATION_RELEASED".equals(event.code())
                                    && !"RESERVATION_REJECTED".equals(event.code()))) {
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        return;
                    }

                    // Case A: Hoàn ví nội bộ
                    if (order.getPaymentMethod() == PaymentMethod.WALLET
                            && order.getPaymentProvider() == PaymentProvider.INTERNAL) {
                        LocalDateTime now = LocalDateTime.now();
                        saga.setStatus(OrderSagaStatus.COMPENSATING);
                        saga.setFailureCode(event.code());
                        saga.setFailureReason(event.reason());
                        saga.setCurrentStep(OrderSagaStep.REFUND_WALLET);
                        saga.setRetryCount(0);
                        saga.setStepDeadline(
                                OrderSagaStep.REFUND_WALLET.timeout() == null
                                        ? null
                                        : now.plus(OrderSagaStep.REFUND_WALLET.timeout()));
                        saga.setUpdatedAt(now);
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.WALLET_SAGA_COMPENSATION_REQUESTED,
                                        new RefundWalletSagaCommand(
                                                "saga:" + saga.getPaymentId(),
                                                order.getId(),
                                                saga.getPaymentId(),
                                                0,
                                                order.getTotalAmount(),
                                                order.getCurrency(),
                                                saga.getFailureReason())));
                        return;
                    }

                    // Case B: Đơn COD không cần hoàn tiền, chỉ nhả kho
                    if (order.getPaymentMethod() == PaymentMethod.COD) {
                        LocalDateTime now = LocalDateTime.now();
                        saga.setStatus(OrderSagaStatus.COMPENSATING);
                        saga.setFailureCode(event.code());
                        saga.setFailureReason(event.reason());
                        saga.setCurrentStep(OrderSagaStep.RELEASE_INVENTORY);
                        saga.setRetryCount(0);
                        saga.setStepDeadline(
                                OrderSagaStep.RELEASE_INVENTORY.timeout() == null
                                        ? null
                                        : now.plus(OrderSagaStep.RELEASE_INVENTORY.timeout()));
                        saga.setUpdatedAt(now);
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.INVENTORY_RELEASE_REQUESTED,
                                        new ReleaseInventoryCommand(
                                                saga.getOrderId(),
                                                saga.getInventoryReservationId())));
                        return;
                    }

                    // Case C: Hoàn tiền trực tuyến
                    if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
                        LocalDateTime now = LocalDateTime.now();
                        saga.setStatus(OrderSagaStatus.COMPENSATING);
                        saga.setFailureCode(event.code());
                        saga.setFailureReason(event.reason());
                        saga.setCurrentStep(OrderSagaStep.REFUND_ORDER_FUNDS);
                        saga.setRetryCount(0);
                        saga.setStepDeadline(
                                OrderSagaStep.REFUND_ORDER_FUNDS.timeout() == null
                                        ? null
                                        : now.plus(OrderSagaStep.REFUND_ORDER_FUNDS.timeout()));
                        saga.setUpdatedAt(now);
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                        new RefundOrderFundsCommand(
                                                "saga-online:" + saga.getPaymentId(),
                                                order.getId(),
                                                saga.getPaymentId(),
                                                order.getTotalAmount(),
                                                order.getCurrency(),
                                                "SAGA_COMPENSATION",
                                                saga.getId())));
                        return;
                    }

                    // Nếu không rõ hình thức thu tiền, đánh dấu cần đối soát
                    saga.setStatus(OrderSagaStatus.FAILED);
                    saga.setFailureCode("CAPTURED_PAYMENT_NEEDS_RECONCILIATION");
                    saga.setFailureReason(event.reason());
                    saga.setStepDeadline(null);
                    saga.setUpdatedAt(LocalDateTime.now());
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    return;
                });
    }

    /** BÙ TRỪ: Kiểm chứng hoàn tiền online; nhả kho hoặc đóng Saga nếu kho đã nhả. */
    @Transactional
    public void orderFundsRefundCredited(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                "order.online-saga-refund-credited.v1",
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    OrderFundsRefundCreditedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), OrderFundsRefundCreditedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.REFUND_ORDER_FUNDS) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.

                    if (!"SAGA_COMPENSATION".equals(event.reasonType())
                            || !saga.getId().equals(event.sourceId())
                            || !Objects.equals(saga.getPaymentId(), event.paymentId())
                            || !("saga-online:" + saga.getPaymentId()).equals(event.refundId())
                            || event.ledgerEntryId() == null
                            || event.ledgerEntryId().isBlank()
                            || event.amount() == null
                            || order.getTotalAmount() == null
                            || event.amount().compareTo(order.getTotalAmount()) != 0) {
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        return;
                    }

                    orderStatusHistoryRepository.save(
                            OrderStatusHistory.builder()
                                    .order(order)
                                    .fromStatus(order.getStatus())
                                    .toStatus(order.getStatus())
                                    .action("SAGA_WALLET_REFUNDED")
                                    .changedBy("PAYMENT_SERVICE")
                                    .reason("Wallet ledger: " + event.ledgerEntryId())
                                    .build());

                    // Nếu kho đã nhả trước đó (case thanh toán muộn), đóng saga luôn
                    if ("LATE_PAYMENT_STOCK_RELEASED".equals(saga.getFailureCode())) {
                        LocalDateTime now = LocalDateTime.now();
                        saga.setStatus(OrderSagaStatus.COMPENSATED);
                        saga.setCurrentStep(OrderSagaStep.DONE);
                        saga.setRetryCount(0);
                        saga.setStepDeadline(null);
                        saga.setUpdatedAt(now);
                        saga.setCompletedAt(now);

                        if (order.getStatus() == OrderStatus.CANCELLED) {
                            // Lưu Saga và command trong cùng transaction.
                            sagaRepository.save(saga);
                            return;
                        }
                        order.cancel("Late payment refunded after inventory release");
                        orderRepository.save(order);
                        promotionService.release(order.getId());
                        orderStatusHistoryRepository.save(
                                OrderStatusHistory.builder()
                                        .order(order)
                                        .fromStatus(OrderStatus.PENDING)
                                        .toStatus(OrderStatus.CANCELLED)
                                        .action("ORDER_CANCELLED")
                                        .changedBy("SYSTEM")
                                        .reason("Late payment refunded after inventory release")
                                        .build());
                        orderNotificationService.sendOrderCancelledNotification(
                                order, "Late payment refunded after inventory release");
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.ORDER_CANCELLED,
                                        new OrderCancelledEvent(
                                                order.getId(),
                                                order.getUserId(),
                                                "Late payment refunded after inventory release")));
                        return;
                    }

                    // Chưa nhả kho thì chuyển sang nhả kho
                    LocalDateTime now = LocalDateTime.now();
                    saga.setStatus(OrderSagaStatus.COMPENSATING);
                    saga.setCurrentStep(OrderSagaStep.RELEASE_INVENTORY);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.RELEASE_INVENTORY.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.RELEASE_INVENTORY.timeout()));
                    saga.setUpdatedAt(now);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.INVENTORY_RELEASE_REQUESTED,
                                    new ReleaseInventoryCommand(
                                            saga.getOrderId(), saga.getInventoryReservationId())));
                    return;
                });
    }

    /** BÙ TRỪ: Kiểm chứng reply rồi đánh dấu FAILED khi hoàn tiền online bị từ chối. */
    @Transactional
    public void orderFundsRefundRejected(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                "order.online-saga-refund-rejected.v1",
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    OrderFundsRefundRejectedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), OrderFundsRefundRejectedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.REFUND_ORDER_FUNDS) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.

                    if (!"SAGA_COMPENSATION".equals(event.reasonType())
                            || !saga.getId().equals(event.sourceId())
                            || !Objects.equals(saga.getPaymentId(), event.paymentId())
                            || !("saga-online:" + saga.getPaymentId()).equals(event.refundId())) {
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        return;
                    }
                    saga.setStatus(OrderSagaStatus.FAILED);
                    saga.setFailureCode(event.code());
                    saga.setFailureReason(event.reason());
                    saga.setStepDeadline(null);
                    saga.setUpdatedAt(LocalDateTime.now());
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    return;
                });
    }

    /** BÙ TRỪ: Kho đã nhả; hoàn tiền muộn nếu cần, hoặc đóng Saga và hủy đơn. */
    @Transactional
    public void inventoryReleased(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_RELEASED,
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    InventoryReleasedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), InventoryReleasedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.RELEASE_INVENTORY) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Xử lý nghiệp vụ, đổi trạng thái và dựng payload của bước tiếp theo.
                    if (!Objects.equals(saga.getInventoryReservationId(), event.reservationId())) {
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        return;
                    }

                    // Nếu là case thanh toán muộn đang đợi nhả kho xong mới hoàn tiền
                    if ("LATE_PAYMENT_STOCK_PENDING".equals(saga.getFailureCode())) {
                        LocalDateTime now = LocalDateTime.now();
                        saga.setStatus(OrderSagaStatus.COMPENSATING);
                        saga.setFailureCode("LATE_PAYMENT_STOCK_RELEASED");
                        saga.setFailureReason(
                                "Late online capture after verified inventory release");
                        saga.setCurrentStep(OrderSagaStep.REFUND_ORDER_FUNDS);
                        saga.setRetryCount(0);
                        saga.setStepDeadline(
                                OrderSagaStep.REFUND_ORDER_FUNDS.timeout() == null
                                        ? null
                                        : now.plus(OrderSagaStep.REFUND_ORDER_FUNDS.timeout()));
                        saga.setUpdatedAt(now);
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        sagaOutbox.emit(
                                saga,
                                SagaCommand.of(
                                        EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                        new RefundOrderFundsCommand(
                                                "saga-online:" + saga.getPaymentId(),
                                                order.getId(),
                                                saga.getPaymentId(),
                                                order.getTotalAmount(),
                                                order.getCurrency(),
                                                "SAGA_COMPENSATION",
                                                saga.getId())));
                        return;
                    }

                    // Hoàn tất bù trừ bình thường
                    String reason = saga.getFailureReason();
                    LocalDateTime now = LocalDateTime.now();
                    saga.setStatus(OrderSagaStatus.COMPENSATED);
                    saga.setCurrentStep(OrderSagaStep.DONE);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(null);
                    saga.setUpdatedAt(now);
                    saga.setCompletedAt(now);

                    if (order.getStatus() == OrderStatus.CANCELLED) {
                        // Lưu Saga và command trong cùng transaction.
                        sagaRepository.save(saga);
                        return;
                    }
                    order.cancel(reason);
                    orderRepository.save(order);
                    promotionService.release(order.getId());
                    orderStatusHistoryRepository.save(
                            OrderStatusHistory.builder()
                                    .order(order)
                                    .fromStatus(OrderStatus.PENDING)
                                    .toStatus(OrderStatus.CANCELLED)
                                    .action("ORDER_CANCELLED")
                                    .changedBy("SYSTEM")
                                    .reason(reason)
                                    .build());
                    orderNotificationService.sendOrderCancelledNotification(order, reason);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.ORDER_CANCELLED,
                                    new OrderCancelledEvent(
                                            order.getId(), order.getUserId(), reason)));
                    return;
                });
    }

    // =========================================================================
    // PHẦN 3: REPLY HOÀN VÍ (TIẾP TỤC NHẢ KHO HOẶC DỪNG ĐỂ ĐỐI SOÁT)
    // =========================================================================

    /** BÙ TRỪ VÍ: Hoàn ví thành công; tiếp tục RELEASE_INVENTORY. */
    @Transactional
    public void walletCompensated(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                "order.wallet-compensated.v1",
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    WalletSagaCompensatedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), WalletSagaCompensatedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.REFUND_WALLET) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Reply hoàn ví phải thuộc đúng payment đang bù trừ.
                    if (!event.paymentId().equals(saga.getPaymentId())) {
                        return;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    saga.setCurrentStep(OrderSagaStep.RELEASE_INVENTORY);
                    saga.setRetryCount(0);
                    saga.setStepDeadline(
                            OrderSagaStep.RELEASE_INVENTORY.timeout() == null
                                    ? null
                                    : now.plus(OrderSagaStep.RELEASE_INVENTORY.timeout()));
                    saga.setUpdatedAt(now);
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    sagaOutbox.emit(
                            saga,
                            SagaCommand.of(
                                    EventTypes.INVENTORY_RELEASE_REQUESTED,
                                    new ReleaseInventoryCommand(
                                            saga.getOrderId(), saga.getInventoryReservationId())));
                    return;
                });
    }

    /** BÙ TRỪ VÍ: Hoàn ví bị từ chối; FAILED để đối soát. */
    @Transactional
    public void walletCompensationRejected(EventEnvelope<?> envelope, String messageId) {
        // 1. Chống xử lý message trùng; lỗi hạ tầng được ném ra để RabbitMQ retry.
        processedMessageService.processOnce(
                messageId,
                "order.wallet-compensation-rejected.v1",
                () -> {
                    // 2. Đọc payload và khóa Order trước Saga để giữ cùng thứ tự khóa.
                    WalletSagaCompensationRejectedEvent event =
                            objectMapper.convertValue(
                                    envelope.payload(), WalletSagaCompensationRejectedEvent.class);
                    if (event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    OrderSaga saga =
                            sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

                    // 3. Event phải thuộc đúng Saga và đúng Order.
                    if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
                        log.warn(
                                "Bỏ qua message {}: Saga {} không tồn tại",
                                messageId,
                                envelope.correlationId());
                        return;
                    }
                    if (!saga.getOrderId().equals(event.orderId())) {
                        log.error(
                                "Bỏ qua message {}: Order {} không thuộc Saga {}",
                                messageId,
                                event.orderId(),
                                saga.getId());
                        return;
                    }

                    // 4. Xử lý trạng thái kết thúc/event muộn trước khi kiểm tra bước hiện tại.
                    if (saga.isTerminal()) {
                        return;
                    }
                    if (saga.getCurrentStep() != OrderSagaStep.REFUND_WALLET) {
                        log.info(
                                "Bỏ qua message {}: Saga {} đang ở bước {}",
                                messageId,
                                saga.getId(),
                                saga.getCurrentStep());
                        return;
                    }

                    // 5. Reply hoàn ví phải thuộc đúng payment đang bù trừ.
                    if (!event.paymentId().equals(saga.getPaymentId())) {
                        return;
                    }
                    saga.setStatus(OrderSagaStatus.FAILED);
                    saga.setFailureCode(event.code());
                    saga.setFailureReason(event.reason());
                    saga.setStepDeadline(null);
                    saga.setUpdatedAt(LocalDateTime.now());
                    // Lưu Saga và command trong cùng transaction.
                    sagaRepository.save(saga);
                    return;
                });
    }
}
