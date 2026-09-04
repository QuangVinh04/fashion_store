package com.fashionstore.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.entity.PaymentStatus;
import com.fashionstore.payment.gateway.PaymentGatewayRegistry;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.PaymentStateService;
import com.fashionstore.payment.service.PaypalPaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalPaymentServiceImpl implements PaypalPaymentService {

    PaymentRepository paymentRepository;
    PaymentGatewayRegistry paymentGatewayRegistry;
    PaymentStateService paymentStateService;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public PaymentResponse capture(String paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        assertPaymentOwner(payment);
        if (payment.getProvider() != PaymentProvider.PAYPAL || payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        PaymentCallbackResult result = paymentGatewayRegistry
                .getCapturableGateway(PaymentProvider.PAYPAL)
                .capture(payment);
        return paymentStateService.applyResult(payment, result);
    }

    private void assertPaymentOwner(Payment payment) {
        if (!payment.getUserId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
    }
}
