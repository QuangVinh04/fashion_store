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

import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Điều phối Saga đặt hàng.
 *
 * <pre>
 * Luồng tiến:  RESERVE_INVENTORY → AUTHORIZE_PAYMENT → CONFIRM_INVENTORY → DONE (COMPLETED)
 * Bù trừ:      [CANCEL_PAYMENT | REFUND_WALLET | REFUND_ORDER_FUNDS] → RELEASE_INVENTORY → DONE
 *              (COMPENSATED, hủy đơn)
 * </pre>
 *
 * <p>Mỗi handler xử lý một event trong một transaction và luôn theo cùng khung: chống trùng
 * ({@code processOnce}) → khóa Order rồi Saga ({@link #lockCheckout}) → kiểm tra Saga đang chờ
 * đúng event này ({@link #atStep}) → chuyển bước trên {@link OrderSaga} → ghi command vào outbox
 * ({@link #emit}). Relay gửi RabbitMQ sau khi transaction commit. Timeout/retry nằm ở
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
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    // =========================================================================
    // PHẦN 1: LUỒNG THÀNH CÔNG (GIỮ KHO → THANH TOÁN → CHỐT KHO → XÁC NHẬN ĐƠN)
    // =========================================================================

    /** BƯỚC 1: RESERVE_INVENTORY → AUTHORIZE_PAYMENT. */
    @Transactional
    public void inventoryReserved(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_RESERVED,
                () -> {
                    InventoryReservationEvent event =
                            read(envelope, InventoryReservationEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null) {
                        return;
                    }
                    Order order = locked.order();
                    OrderSaga saga = locked.saga();

                    // Giữ kho về muộn khi Saga đã đóng: nhả reservation mồ côi.
                    if (saga.isTerminal()) {
                        if (saga.getInventoryReservationId() == null) {
                            saga.recordOrphanReservation(event.reservationId());
                            emit(
                                    saga,
                                    EventTypes.INVENTORY_RELEASE_REQUESTED,
                                    new ReleaseInventoryCommand(
                                            saga.getOrderId(), event.reservationId()));
                        }
                        return;
                    }
                    if (!atStep(saga, OrderSagaStep.RESERVE_INVENTORY, messageId)) {
                        return;
                    }

                    // [1.1] Lưu ID giữ kho, sang AUTHORIZE_PAYMENT
                    saga.inventoryReserved(event.reservationId());

                    // [1.2] Yêu cầu thanh toán, kèm deadline của bước
                    emit(
                            saga,
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
                                            .toInstant()));
                });
    }

    /** BƯỚC 1b: Lưu link/QR thanh toán; Saga vẫn chờ ở AUTHORIZE_PAYMENT. */
    @Transactional
    public void paymentInitiated(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_INITIATED,
                () -> {
                    PaymentInitiatedEvent event = read(envelope, PaymentInitiatedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.AUTHORIZE_PAYMENT, messageId)) {
                        return;
                    }

                    Order order = locked.order();
                    order.setPaymentUrl(event.paymentUrl());
                    orderRepository.save(order);
                });
    }

    /** BƯỚC 2: AUTHORIZE_PAYMENT → CONFIRM_INVENTORY. */
    @Transactional
    public void paymentCompleted(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_COMPLETED,
                () -> {
                    PaymentSuccessEvent event = read(envelope, PaymentSuccessEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    // Tiền về khi Saga đã đóng hoặc đang hủy thanh toán/nhả kho.
                    if (saga.isTerminal()
                            || saga.getCurrentStep() == OrderSagaStep.CANCEL_PAYMENT
                            || saga.getCurrentStep() == OrderSagaStep.RELEASE_INVENTORY) {
                        recoverLatePayment(locked.order(), saga, event);
                        return;
                    }
                    // Reply "không huỷ được" đã đẩy saga tới CONFIRM_INVENTORY trước khi event này đến
                    // (hai queue riêng, không đảm bảo thứ tự): chỉ ghi nhận paymentId, không đổi bước.
                    if (saga.getCurrentStep() == OrderSagaStep.CONFIRM_INVENTORY
                            && !saga.isTerminal()
                            && saga.getPaymentId() == null
                            && event.paymentId() != null) {
                        saga.setPaymentId(event.paymentId());
                        return;
                    }
                    if (!atStep(saga, OrderSagaStep.AUTHORIZE_PAYMENT, messageId)) {
                        return;
                    }

                    // [2.1] Ghi nhận paymentId, sang CONFIRM_INVENTORY
                    saga.paymentAuthorized(event.paymentId());

                    // [2.2] Yêu cầu chốt kho
                    emit(
                            saga,
                            EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                            confirmInventoryCommand(saga));
                });
    }

    /** BƯỚC 3: CONFIRM_INVENTORY → DONE; xác nhận đơn, coupon, lịch sử, giỏ hàng, thông báo. */
    @Transactional
    public void inventoryConfirmed(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_CONFIRMED,
                () -> {
                    InventoryConfirmedEvent event = read(envelope, InventoryConfirmedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.CONFIRM_INVENTORY, messageId)) {
                        return;
                    }
                    Order order = locked.order();
                    OrderSaga saga = locked.saga();

                    // [3.1] Saga hoàn tất thành công
                    saga.complete();

                    // [3.2] Xác nhận đơn và coupon
                    order.confirm(saga.getPaymentId());
                    orderRepository.save(order);
                    promotionService.confirm(order.getId());

                    // [3.3] Ghi lịch sử trạng thái
                    recordHistory(
                            order,
                            OrderStatus.PENDING,
                            OrderStatus.CONFIRMED,
                            "ORDER_CONFIRMED",
                            "SYSTEM",
                            "Saga hoàn tất thanh toán và giữ kho thành công");

                    // [3.4] Bớt đúng số lượng đã đặt khỏi giỏ hàng
                    removeOrderedItemsFromCart(order);

                    // [3.5] Thông báo cho khách hàng
                    orderNotificationService.sendOrderConfirmedNotification(order);

                    // [3.6] Phát sự kiện đơn đã xác nhận
                    emit(
                            saga,
                            EventTypes.ORDER_CONFIRMED,
                            new OrderConfirmedEvent(
                                    order.getId(), order.getUserId(), order.getCheckoutId()));
                });
    }

    // =========================================================================
    // PHẦN 2: BÙ TRỪ VÀ PHỤC HỒI (HỦY/HOÀN TIỀN → NHẢ KHO → HỦY ĐƠN)
    // =========================================================================

    /** BÙ TRỪ: Không giữ được kho; chưa giữ tài nguyên nào nên đóng Saga và hủy đơn ngay. */
    @Transactional
    public void inventoryRejected(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_REJECTED,
                () -> {
                    InventoryReservationFailedEvent event =
                            read(envelope, InventoryReservationFailedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.RESERVE_INVENTORY, messageId)) {
                        return;
                    }

                    locked.saga().compensated(event.failureCode(), event.failureMessage());
                    cancelOrder(locked.order(), locked.saga(), event.failureMessage());
                });
    }

    /** BÙ TRỪ: Thanh toán lỗi; AUTHORIZE_PAYMENT → RELEASE_INVENTORY. */
    @Transactional
    public void paymentFailed(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_FAILED,
                () -> {
                    PaymentFailedEvent event = read(envelope, PaymentFailedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.AUTHORIZE_PAYMENT, messageId)) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    saga.startCompensation(
                            OrderSagaStep.RELEASE_INVENTORY, "PAYMENT_FAILED", event.reason());
                    emit(saga, EventTypes.INVENTORY_RELEASE_REQUESTED, releaseInventoryCommand(saga));
                });
    }

    /** BÙ TRỪ: Hủy thanh toán thành công; CANCEL_PAYMENT → RELEASE_INVENTORY. */
    @Transactional
    public void paymentCancelled(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_CANCELLED,
                () -> {
                    PaymentCancelledEvent event = read(envelope, PaymentCancelledEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.CANCEL_PAYMENT, messageId)) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    saga.releaseInventory();
                    emit(saga, EventTypes.INVENTORY_RELEASE_REQUESTED, releaseInventoryCommand(saga));
                });
    }

    /** PHỤC HỒI TIẾN: Không hủy được vì tiền đã thu; CANCEL_PAYMENT → CONFIRM_INVENTORY. */
    @Transactional
    public void paymentCancellationRejected(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.PAYMENT_CANCELLATION_REJECTED,
                () -> {
                    PaymentCancellationRejectedEvent event =
                            read(envelope, PaymentCancellationRejectedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.CANCEL_PAYMENT, messageId)) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    saga.resumeAfterCancellationRejected();
                    // Tiền đã thu: payment.completed có thể đến sau, không được để đơn mất paymentId.
                    if (saga.getPaymentId() == null) {
                        saga.setPaymentId(event.paymentId());
                    }
                    emit(
                            saga,
                            EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                            confirmInventoryCommand(saga));
                });
    }

    /** BÙ TRỪ: Chốt kho bị từ chối; hoàn ví, hoàn tiền online hoặc chỉ nhả kho (COD). */
    @Transactional
    public void inventoryConfirmationRejected(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_CONFIRMATION_REJECTED,
                () -> {
                    InventoryConfirmationRejectedEvent event =
                            read(envelope, InventoryConfirmationRejectedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.CONFIRM_INVENTORY, messageId)) {
                        return;
                    }
                    Order order = locked.order();
                    OrderSaga saga = locked.saga();

                    if (!Objects.equals(saga.getInventoryReservationId(), event.reservationId())
                            || (!"RESERVATION_RELEASED".equals(event.code())
                                    && !"RESERVATION_REJECTED".equals(event.code()))) {
                        logIgnored(messageId, saga, "reply chốt kho không khớp reservation/code");
                        return;
                    }

                    // Case A: Ví nội bộ → hoàn ví
                    if (order.getPaymentMethod() == PaymentMethod.WALLET
                            && order.getPaymentProvider() == PaymentProvider.INTERNAL) {
                        saga.startCompensation(
                                OrderSagaStep.REFUND_WALLET, event.code(), event.reason());
                        emit(
                                saga,
                                EventTypes.WALLET_SAGA_COMPENSATION_REQUESTED,
                                new RefundWalletSagaCommand(
                                        "saga:" + saga.getPaymentId(),
                                        order.getId(),
                                        saga.getPaymentId(),
                                        0,
                                        order.getTotalAmount(),
                                        order.getCurrency(),
                                        saga.getFailureReason()));
                        return;
                    }

                    // Case B: COD chưa thu tiền → chỉ nhả kho
                    if (order.getPaymentMethod() == PaymentMethod.COD) {
                        saga.startCompensation(
                                OrderSagaStep.RELEASE_INVENTORY, event.code(), event.reason());
                        emit(
                                saga,
                                EventTypes.INVENTORY_RELEASE_REQUESTED,
                                releaseInventoryCommand(saga));
                        return;
                    }

                    // Case C: Online → hoàn tiền
                    if (order.getPaymentMethod() == PaymentMethod.ONLINE) {
                        saga.startCompensation(
                                OrderSagaStep.REFUND_ORDER_FUNDS, event.code(), event.reason());
                        emit(
                                saga,
                                EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                refundOrderFundsCommand(order, saga));
                        return;
                    }

                    // Không rõ hình thức thu tiền → dừng để đối soát
                    failSaga(saga, "CAPTURED_PAYMENT_NEEDS_RECONCILIATION", event.reason());
                    sagaRepository.save(saga);
                });
    }

    /** BÙ TRỪ: Hoàn tiền online xong; REFUND_ORDER_FUNDS → RELEASE_INVENTORY (hoặc DONE). */
    @Transactional
    public void orderFundsRefundCredited(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.ORDER_FUNDS_REFUND_CREDITED,
                () -> {
                    OrderFundsRefundCreditedEvent event =
                            read(envelope, OrderFundsRefundCreditedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(
                                    locked.saga(), OrderSagaStep.REFUND_ORDER_FUNDS, messageId)) {
                        return;
                    }
                    Order order = locked.order();
                    OrderSaga saga = locked.saga();

                    if (!"SAGA_COMPENSATION".equals(event.reasonType())
                            || !saga.getId().equals(event.sourceId())
                            || !Objects.equals(saga.getPaymentId(), event.paymentId())
                            || !refundId(saga).equals(event.refundId())
                            || event.ledgerEntryId() == null
                            || event.ledgerEntryId().isBlank()
                            || event.amount() == null
                            || order.getTotalAmount() == null
                            || event.amount().compareTo(order.getTotalAmount()) != 0) {
                        logIgnored(messageId, saga, "reply hoàn tiền không khớp yêu cầu hoàn");
                        return;
                    }

                    recordHistory(
                            order,
                            order.getStatus(),
                            order.getStatus(),
                            "SAGA_WALLET_REFUNDED",
                            "PAYMENT_SERVICE",
                            "Wallet ledger: " + event.ledgerEntryId());

                    // Tiền về muộn sau khi kho đã nhả → đóng Saga và hủy đơn
                    if ("LATE_PAYMENT_STOCK_RELEASED".equals(saga.getFailureCode())) {
                        saga.compensated();
                        cancelOrder(order, saga, "Late payment refunded after inventory release");
                        return;
                    }

                    // Kho chưa nhả → nhả kho
                    saga.releaseInventory();
                    emit(saga, EventTypes.INVENTORY_RELEASE_REQUESTED, releaseInventoryCommand(saga));
                });
    }

    /** BÙ TRỪ: Hoàn tiền online bị từ chối; FAILED để đối soát. */
    @Transactional
    public void orderFundsRefundRejected(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.ORDER_FUNDS_REFUND_REJECTED,
                () -> {
                    OrderFundsRefundRejectedEvent event =
                            read(envelope, OrderFundsRefundRejectedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(
                                    locked.saga(), OrderSagaStep.REFUND_ORDER_FUNDS, messageId)) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    if (!"SAGA_COMPENSATION".equals(event.reasonType())
                            || !saga.getId().equals(event.sourceId())
                            || !Objects.equals(saga.getPaymentId(), event.paymentId())
                            || !refundId(saga).equals(event.refundId())) {
                        logIgnored(messageId, saga, "reply hoàn tiền không khớp yêu cầu hoàn");
                        return;
                    }

                    failSaga(saga, event.code(), event.reason());
                    sagaRepository.save(saga);
                });
    }

    /** BÙ TRỪ: Kho đã nhả; RELEASE_INVENTORY → DONE và hủy đơn (hoặc hoàn tiền về muộn). */
    @Transactional
    public void inventoryReleased(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.INVENTORY_RELEASED,
                () -> {
                    InventoryReleasedEvent event = read(envelope, InventoryReleasedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.RELEASE_INVENTORY, messageId)) {
                        return;
                    }
                    Order order = locked.order();
                    OrderSaga saga = locked.saga();

                    if (!Objects.equals(saga.getInventoryReservationId(), event.reservationId())) {
                        logIgnored(messageId, saga, "reservation đã nhả không khớp");
                        return;
                    }

                    // Tiền về muộn đang chờ nhả kho xong → giờ mới hoàn tiền
                    if ("LATE_PAYMENT_STOCK_PENDING".equals(saga.getFailureCode())) {
                        saga.startCompensation(
                                OrderSagaStep.REFUND_ORDER_FUNDS,
                                "LATE_PAYMENT_STOCK_RELEASED",
                                "Late online capture after verified inventory release");
                        emit(
                                saga,
                                EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                refundOrderFundsCommand(order, saga));
                        return;
                    }

                    // Bù trừ xong → đóng Saga và hủy đơn
                    saga.compensated();
                    cancelOrder(order, saga, saga.getFailureReason());
                });
    }

    // =========================================================================
    // PHẦN 3: REPLY HOÀN VÍ (TIẾP TỤC NHẢ KHO HOẶC DỪNG ĐỂ ĐỐI SOÁT)
    // =========================================================================

    /** BÙ TRỪ VÍ: Hoàn ví thành công; REFUND_WALLET → RELEASE_INVENTORY. */
    @Transactional
    public void walletCompensated(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.WALLET_COMPENSATED,
                () -> {
                    WalletSagaCompensatedEvent event =
                            read(envelope, WalletSagaCompensatedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.REFUND_WALLET, messageId)) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    if (!Objects.equals(event.paymentId(), saga.getPaymentId())) {
                        logIgnored(messageId, saga, "reply hoàn ví khác paymentId");
                        return;
                    }

                    saga.releaseInventory();
                    emit(saga, EventTypes.INVENTORY_RELEASE_REQUESTED, releaseInventoryCommand(saga));
                });
    }

    /** BÙ TRỪ VÍ: Hoàn ví bị từ chối; FAILED để đối soát. */
    @Transactional
    public void walletCompensationRejected(EventEnvelope<?> envelope, String messageId) {
        processedMessageService.processOnce(
                messageId,
                SagaConsumers.WALLET_COMPENSATION_REJECTED,
                () -> {
                    WalletSagaCompensationRejectedEvent event =
                            read(envelope, WalletSagaCompensationRejectedEvent.class);
                    Locked locked = lockCheckout(envelope, event.orderId(), messageId);
                    if (locked == null
                            || !atStep(locked.saga(), OrderSagaStep.REFUND_WALLET, messageId)) {
                        return;
                    }
                    OrderSaga saga = locked.saga();

                    if (!Objects.equals(event.paymentId(), saga.getPaymentId())) {
                        logIgnored(messageId, saga, "reply hoàn ví khác paymentId");
                        return;
                    }

                    failSaga(saga, event.code(), event.reason());
                    sagaRepository.save(saga);
                });
    }

    // =========================================================================
    // PHỤC HỒI TIỀN VỀ MUỘN
    // =========================================================================

    /**
     * Tiền online về khi Saga đã đóng hoặc đang lùi: giữ paymentId, rồi hoàn tiền (sau khi kho đã
     * nhả). Trường hợp không chắc chắn chỉ ghi lịch sử để người kiểm tra.
     */
    private void recoverLatePayment(Order order, OrderSaga saga, PaymentSuccessEvent event) {
        boolean stockReleased = saga.getStatus() == OrderSagaStatus.COMPENSATED;

        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            return;
        }
        if (saga.getStatus() == OrderSagaStatus.COMPLETED) {
            // Đơn đã xác nhận trước khi payment.completed đến: chỉ bù paymentId còn thiếu.
            if (order.getPaymentId() == null && event.paymentId() != null) {
                saga.setPaymentId(event.paymentId());
                order.setPaymentId(event.paymentId());
                orderRepository.save(order);
            }
            return;
        }
        if (event.paymentId() == null
                || event.paymentId().isBlank()
                || saga.getPaymentId() != null && !saga.getPaymentId().equals(event.paymentId())) {
            recordHistory(
                    order,
                    order.getStatus(),
                    order.getStatus(),
                    "LATE_PAYMENT_REVIEW_REQUIRED",
                    "PAYMENT_SERVICE",
                    "Late capture payment identity does not match the checkout saga");
            return;
        }

        saga.setPaymentId(event.paymentId());
        order.setPaymentId(event.paymentId());
        orderRepository.save(order);

        // Đơn đã đi tiếp (giao hàng/khác PENDING-CANCELLED) hoặc thiếu reservation → kiểm tra tay
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CANCELLED
                || order.getShipmentInitiationToken() != null
                || order.getTrackingCode() != null && !order.getTrackingCode().isBlank()
                || saga.getInventoryReservationId() == null
                || saga.getStatus() == OrderSagaStatus.FAILED) {
            recordHistory(
                    order,
                    order.getStatus(),
                    order.getStatus(),
                    "LATE_PAYMENT_REVIEW_REQUIRED",
                    "PAYMENT_SERVICE",
                    "Verified late funds retained; fulfillment and inventory disposition need"
                            + " verification");
            sagaRepository.save(saga);
            return;
        }

        // Kho đã nhả → hoàn tiền ngay
        if (stockReleased) {
            saga.startCompensation(
                    OrderSagaStep.REFUND_ORDER_FUNDS,
                    "LATE_PAYMENT_STOCK_RELEASED",
                    "Late online capture after verified inventory release");
            emit(saga, EventTypes.ORDER_FUNDS_REFUND_REQUESTED, refundOrderFundsCommand(order, saga));
            return;
        }

        // Kho chưa nhả → nhả kho trước, inventoryReleased sẽ hoàn tiền
        saga.startCompensation(
                OrderSagaStep.RELEASE_INVENTORY,
                "LATE_PAYMENT_STOCK_PENDING",
                "Late online capture; refund waits for inventory release acknowledgement");
        emit(saga, EventTypes.INVENTORY_RELEASE_REQUESTED, releaseInventoryCommand(saga));
    }

    // =========================================================================
    // KHUNG CHUNG CỦA HANDLER
    // =========================================================================

    private record Locked(Order order, OrderSaga saga) {}

    private <T> T read(EventEnvelope<?> envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.payload(), type);
    }

    /**
     * Khóa Order trước Saga (cùng thứ tự khóa ở mọi nơi). Trả null nếu event không thuộc Saga
     * checkout của đúng Order này.
     */
    private Locked lockCheckout(EventEnvelope<?> envelope, String orderId, String messageId) {
        if (orderId == null) {
            return null;
        }
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        OrderSaga saga = sagaRepository.findByIdForUpdate(envelope.correlationId()).orElse(null);

        if (saga == null || saga.getType() != OrderSagaType.CHECKOUT) {
            log.warn("Bỏ qua message {}: Saga {} không tồn tại", messageId, envelope.correlationId());
            return null;
        }
        if (!saga.getOrderId().equals(orderId)) {
            log.error(
                    "Bỏ qua message {}: Order {} không thuộc Saga {}",
                    messageId,
                    orderId,
                    saga.getId());
            return null;
        }
        if (order == null) {
            throw new IllegalStateException(
                    "Saga " + saga.getId() + " trỏ tới Order " + orderId + " không tồn tại");
        }
        return new Locked(order, saga);
    }

    /** Saga còn chạy và đang chờ đúng bước này; event trùng/muộn của bước khác bị bỏ qua. */
    private boolean atStep(OrderSaga saga, OrderSagaStep expected, String messageId) {
        if (saga.isTerminal()) {
            return false;
        }
        if (saga.getCurrentStep() != expected) {
            log.info(
                    "Bỏ qua message {}: Saga {} đang ở bước {}",
                    messageId,
                    saga.getId(),
                    saga.getCurrentStep());
            return false;
        }
        return true;
    }

    private void logIgnored(String messageId, OrderSaga saga, String reason) {
        log.warn("Bỏ qua message {}: {} (Saga {})", messageId, reason, saga.getId());
    }

    /** Lưu Saga và ghi command vào outbox trong cùng transaction. */
    private void emit(OrderSaga saga, String eventType, Object payload) {
        sagaRepository.save(saga);
        sagaOutbox.emit(saga, SagaCommand.of(eventType, payload));
    }

    /** Bước cuối của bù trừ: hủy đơn, nhả coupon, ghi lịch sử, báo khách, phát ORDER_CANCELLED. */
    private void cancelOrder(Order order, OrderSaga saga, String reason) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            sagaRepository.save(saga);
            return;
        }
        order.cancel(reason);
        orderRepository.save(order);
        promotionService.release(order.getId());
        recordHistory(
                order, OrderStatus.PENDING, OrderStatus.CANCELLED, "ORDER_CANCELLED", "SYSTEM", reason);
        orderNotificationService.sendOrderCancelledNotification(order, reason);
        emit(
                saga,
                EventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(order.getId(), order.getUserId(), reason));
    }

    private void removeOrderedItemsFromCart(Order order) {
        Cart cart =
                cartRepository
                        .findByUserIdAndStatus(order.getUserId(), CartStatus.ACTIVE)
                        .orElse(null);
        if (cart == null) {
            return;
        }
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
                            Integer ordered = orderedQuantities.get(item.getVariantId());
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

    private void recordHistory(
            Order order,
            OrderStatus from,
            OrderStatus to,
            String action,
            String changedBy,
            String reason) {
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(from)
                        .toStatus(to)
                        .action(action)
                        .changedBy(changedBy)
                        .reason(reason)
                        .build());
    }

    // ----- payload command -----

    /**
     * Saga FAILED nghĩa là tiền hoặc tồn kho đang treo và không còn bước tự động nào — phải kêu lên
     * (metric + log), không chỉ đổi trạng thái.
     */
    private void failSaga(OrderSaga saga, String code, String reason) {
        saga.fail(code, reason);
        meterRegistry.counter("order.saga.failed", "step", saga.getCurrentStep().name()).increment();
        log.error(
                "Saga {} (order {}) FAILED ở bước {} [{}]: {} — cần can thiệp thủ công",
                saga.getId(),
                saga.getOrderId(),
                saga.getCurrentStep(),
                code,
                reason);
    }

    private static String refundId(OrderSaga saga) {
        return "saga-online:" + saga.getPaymentId();
    }

    private static ReleaseInventoryCommand releaseInventoryCommand(OrderSaga saga) {
        return new ReleaseInventoryCommand(saga.getOrderId(), saga.getInventoryReservationId());
    }

    private static ConfirmInventoryCommand confirmInventoryCommand(OrderSaga saga) {
        return new ConfirmInventoryCommand(saga.getOrderId(), saga.getInventoryReservationId());
    }

    private static RefundOrderFundsCommand refundOrderFundsCommand(Order order, OrderSaga saga) {
        return new RefundOrderFundsCommand(
                refundId(saga),
                order.getId(),
                saga.getPaymentId(),
                order.getTotalAmount(),
                order.getCurrency(),
                "SAGA_COMPENSATION",
                saga.getId());
    }
}
