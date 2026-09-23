package com.fashionstore.payment.service.provider;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.exception.PaymentErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentHandlerRegistry {
    private final Map<PaymentProvider, PaymentHandler> handlers;

    public PaymentHandlerRegistry(List<PaymentHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(PaymentHandler::provider, Function.identity()));
    }

    public PaymentHandler get(PaymentProvider provider) {
        PaymentHandler handler = handlers.get(provider);
        if (handler == null) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return handler;
    }
}
