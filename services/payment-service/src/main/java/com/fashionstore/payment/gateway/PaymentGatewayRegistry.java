package com.fashionstore.payment.gateway;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.exception.PaymentErrorCode;
import com.fashionstore.common.payment.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
public class PaymentGatewayRegistry {
    private final Map<PaymentProvider, PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.gateways = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::provider, Function.identity()));
    }

    public PaymentGateway get(PaymentProvider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return gateway;
    }

    public CallbackPaymentGateway getCallbackGateway(PaymentProvider provider) {
        PaymentGateway gateway = get(provider);
        if (!(gateway instanceof CallbackPaymentGateway callbackGateway)) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return callbackGateway;
    }

    public CapturablePaymentGateway getCapturableGateway(PaymentProvider provider) {
        PaymentGateway gateway = get(provider);
        if (!(gateway instanceof CapturablePaymentGateway capturableGateway)) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return capturableGateway;
    }

    /** Returns a provider gateway that supports refunds. */
    public RefundablePaymentGateway getRefundableGateway(PaymentProvider provider) {
        PaymentGateway gateway = get(provider);
        if (!(gateway instanceof RefundablePaymentGateway refundableGateway)) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return refundableGateway;
    }
}
