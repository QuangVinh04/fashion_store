package com.fashionstore.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentStatus;
import com.fashionstore.payment.gateway.PaymentGatewayRegistry;
import com.fashionstore.payment.mapper.PaymentResponseMapper;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.PaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentServiceImpl implements PaymentService {

    PaymentRepository paymentRepository;
    PaymentGatewayRegistry paymentGatewayRegistry;
    PaymentResponseMapper paymentResponseMapper;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        assertPaymentOwner(payment);
        return paymentResponseMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentInitiationResult initiate(String paymentId, String clientIp) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        assertPaymentOwner(payment);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_STATUS_INVALID);
        }
        if (payment.getMerchantReference() == null) {
            payment.setMerchantReference(payment.getId().replace("-", ""));
            paymentRepository.save(payment);
        }

        PaymentInitiationResult result = paymentGatewayRegistry.get(payment.getProvider()).initiate(payment, clientIp);
        if (result.getProviderTransactionId() != null) {
            payment.setTransactionId(result.getProviderTransactionId());
        }
        payment.setProviderAmount(result.getProviderAmount());
        payment.setProviderCurrency(result.getProviderCurrency());
        paymentRepository.save(payment);
        return result;
    }

    private void assertPaymentOwner(Payment payment) {
        if (!payment.getUserId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
    }
}
