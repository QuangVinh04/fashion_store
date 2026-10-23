package com.fashionstore.payment.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.event.PaymentFailedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.payment.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.payment.dto.PaymentResponse;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.payment.mapper.PaymentResponseMapper;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.payment.service.PaymentStateService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentStateServiceImpl implements PaymentStateService {

    PaymentRepository paymentRepository;
    PaymentResponseMapper paymentResponseMapper;
    OutboxEventRecorder outboxEventRecorder;

    @Override
    @Transactional
    public PaymentResponse applyResult(Payment payment, PaymentCallbackResult result) {
        boolean verifiedLateCapture =
                result.getStatus() == PaymentStatus.COMPLETED
                        && payment.getMethod()
                                == com.fashionstore.common.payment.PaymentMethod.ONLINE
                        && (payment.getStatus() == PaymentStatus.CANCELLED
                                || payment.getStatus() == PaymentStatus.FAILED);
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.INITIATING
                && payment.getStatus() != PaymentStatus.INITIATION_UNKNOWN
                && !verifiedLateCapture) {
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        if (result.getStatus() == PaymentStatus.COMPLETED
                && !isProviderAmountValid(payment, result)) {
            throw new AppException(PaymentErrorCode.PAYMENT_AMOUNT_INVALID);
        }

        payment.setStatus(result.getStatus());
        if (result.getStatus() != PaymentStatus.PENDING) {
            String txnId = result.getProviderTransactionId() != null && !result.getProviderTransactionId().isBlank()
                    ? result.getProviderTransactionId()
                    : result.getMerchantReference();
            payment.setTransactionId(txnId);
        }
        payment.setFailureReason(result.getFailureReason());
        if (result.getStatus() == PaymentStatus.COMPLETED) {
            payment.setPaidAt(LocalDateTime.now());
        }

        Payment saved = paymentRepository.save(payment);
        publishOrderStatusEvent(saved);
        return paymentResponseMapper.toResponse(saved);
    }

    @Override
    public boolean isProviderAmountValid(Payment payment, PaymentCallbackResult result) {
        return payment.getProviderAmount() != null
                && payment.getProviderCurrency() != null
                && result.getAmount() != null
                && payment.getProviderAmount().compareTo(result.getAmount()) == 0
                && payment.getProviderCurrency().equals(result.getCurrency());
    }

    private void publishOrderStatusEvent(Payment payment) {
        if (payment.getType() == com.fashionstore.common.payment.PaymentType.TOP_UP
                || payment.getOrderId() == null) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            outboxEventRecorder.saveMessage(
                    payment.getOrderId(),
                    EventTypes.PAYMENT_COMPLETED,
                    EventEnvelope.v1(
                            EventTypes.PAYMENT_COMPLETED,
                            payment.getOrderId(),
                            correlationId(payment),
                            new PaymentSuccessEvent(payment.getOrderId(), payment.getId(), 0)));
            return;
        }

        if (payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED) {
            outboxEventRecorder.saveMessage(
                    payment.getOrderId(),
                    EventTypes.PAYMENT_FAILED,
                    EventEnvelope.v1(
                            EventTypes.PAYMENT_FAILED,
                            payment.getOrderId(),
                            correlationId(payment),
                            new PaymentFailedEvent(
                                    payment.getOrderId(),
                                    payment.getId(),
                                    payment.getFailureReason(),
                                    0)));
        }
    }

    /**
     * Event sinh ra từ callback của cổng thanh toán vẫn phải mang sagaId, nếu không order-service
     * tra không thấy saga và lặng lẽ bỏ qua reply.
     */
    private String correlationId(Payment payment) {
        return payment.getSagaId() == null ? payment.getOrderId() : payment.getSagaId();
    }
}
