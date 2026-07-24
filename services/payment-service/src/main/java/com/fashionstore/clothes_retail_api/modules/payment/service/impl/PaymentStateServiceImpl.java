package com.fashionstore.product.modules.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.common.exception.ErrorCode;
import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.dto.PaymentResponse;
import com.fashionstore.product.modules.payment.entity.Payment;
import com.fashionstore.product.modules.payment.entity.PaymentStatus;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.EventTypes;
import com.fashionstore.contracts.payment.PaymentResult;
import com.fashionstore.product.modules.payment.mapper.PaymentResponseMapper;
import com.fashionstore.product.modules.payment.repository.PaymentRepository;
import com.fashionstore.product.modules.payment.service.PaymentStateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentStateServiceImpl implements PaymentStateService {

    PaymentRepository paymentRepository;
    PaymentResponseMapper paymentResponseMapper;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PaymentResponse applyResult(Payment payment, PaymentCallbackResult result) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_STATUS_INVALID);
        }
        if (!isProviderAmountValid(payment, result)) {
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }

        payment.setStatus(result.getStatus());
        if (result.getStatus() != PaymentStatus.PENDING) {
            payment.setTransactionId(result.getProviderTransactionId());
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
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            eventPublisher.publishEvent(EventEnvelope.v1(
                    EventTypes.PAYMENT_COMPLETED,
                    payment.getOrderId(),
                    payment.getOrderId(),
                    new PaymentResult(payment.getOrderId(), payment.getId(), null)
            ));
            return;
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            eventPublisher.publishEvent(EventEnvelope.v1(
                    EventTypes.PAYMENT_FAILED,
                    payment.getOrderId(),
                    payment.getOrderId(),
                    new PaymentResult(payment.getOrderId(), payment.getId(), payment.getFailureReason())
            ));
        }
    }
}
