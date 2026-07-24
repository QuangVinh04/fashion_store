package com.fashionstore.product.modules.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.common.exception.ErrorCode;
import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.dto.VnPayIpnResponse;
import com.fashionstore.product.modules.payment.entity.Payment;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.product.modules.payment.entity.PaymentStatus;
import com.fashionstore.product.modules.payment.gateway.PaymentGatewayRegistry;
import com.fashionstore.product.modules.payment.repository.PaymentRepository;
import com.fashionstore.product.modules.payment.service.PaymentStateService;
import com.fashionstore.product.modules.payment.service.VnPayPaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnPayPaymentServiceImpl implements VnPayPaymentService {

    PaymentRepository paymentRepository;
    PaymentGatewayRegistry paymentGatewayRegistry;
    PaymentStateService paymentStateService;

    @Override
    @Transactional(readOnly = true)
    public PaymentCallbackResult verifyReturn(Map<String, String> payload) {
        PaymentCallbackResult result = verifyCallback(payload);
        if (!result.isSignatureValid()) {
            throw new AppException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        return result;
    }

    @Override
    @Transactional
    public VnPayIpnResponse processIpn(Map<String, String> payload) {
        if (payload.isEmpty()) {
            return new VnPayIpnResponse("99", "Invalid request");
        }

        PaymentCallbackResult result = verifyCallback(payload);
        if (!result.isSignatureValid()) {
            return new VnPayIpnResponse("97", "Invalid signature");
        }

        Payment payment = paymentRepository.findByMerchantReference(result.getMerchantReference()).orElse(null);
        if (payment == null) {
            return new VnPayIpnResponse("01", "Payment not found");
        }
        if (!paymentStateService.isProviderAmountValid(payment, result)) {
            return new VnPayIpnResponse("04", "Invalid amount");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new VnPayIpnResponse("02", "Payment already updated");
        }

        paymentStateService.applyResult(payment, result);
        return new VnPayIpnResponse("00", "Confirm success");
    }

    private PaymentCallbackResult verifyCallback(Map<String, String> payload) {
        return paymentGatewayRegistry
                .getCallbackGateway(PaymentProvider.VNPAY)
                .verifyCallback(payload);
    }
}
