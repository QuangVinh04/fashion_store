package com.fashionstore.payment.payment.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.wallet.exception.WalletErrorCode;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.order.OrderDeliveredEvent;
import com.fashionstore.payment.shared.config.messaging.RabbitMQNames;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Delivery evidence is distinct from verified customer cash collection. */
@Component
@RequiredArgsConstructor
public class OrderDeliveredEventListener {

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;

    /** Processes a delivered-order event exactly once. */
    @Transactional
    @RabbitListener(queues = RabbitMQNames.PAYMENT_ORDER_DELIVERED_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "payment-order-delivered-v1", () -> {
            OrderDeliveredEvent event = objectMapper.convertValue(envelope.payload(), OrderDeliveredEvent.class);
            Payment payment = paymentRepository.findByOrderIdForUpdate(event.orderId()).orElse(null);
            if (payment == null) {
                return;
            }
            if (payment.getDeliveryRecordedAt() == null && event.deliveredAt() != null) {
                payment.setDeliveryRecordedAt(event.deliveredAt().toInstant(ZoneOffset.UTC));
            }
            boolean cashEvidencePresent = event.codCollectedAmount() != null || event.codCollectedAt() != null
                    || event.trackingCode() != null || event.carrierShopId() != null;
            if (payment.getMethod() == PaymentMethod.COD && cashEvidencePresent) {
                if (payment.getType() != PaymentType.ORDER
                        || payment.getProvider() != PaymentProvider.COD
                        || !"VND".equals(payment.getCurrency()) || event.codCollectedAmount() == null || event.codCollectedAmount().signum() <= 0
                        || event.codCollectedAmount().stripTrailingZeros().scale() > 0
                        || event.codCollectedAmount().compareTo(payment.getAmount()) != 0
                        || event.codCollectedAt() == null || event.codCollectedAt().isAfter(Instant.now().plusSeconds(300))
                        || event.trackingCode() == null || event.trackingCode().isBlank() || event.trackingCode().length() > 100
                        || event.carrierShopId() == null || !event.carrierShopId().matches("[1-9][0-9]*")) {
                    throw new AppException(WalletErrorCode.SOURCE_INVALID);
                }
                if (payment.getStatus() == PaymentStatus.COD_PENDING || payment.getStatus() == PaymentStatus.CANCELLED) {
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setOperationId("ghn:" + event.carrierShopId() + ":" + event.trackingCode());
                    payment.setPaidAt(LocalDateTime.ofInstant(event.codCollectedAt(), ZoneId.systemDefault()));
                    payment.setFailureReason(null);
                }
            }
            paymentRepository.save(payment);
        });
    }
}
