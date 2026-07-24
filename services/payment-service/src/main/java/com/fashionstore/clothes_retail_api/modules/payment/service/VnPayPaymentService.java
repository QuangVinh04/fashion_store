package com.fashionstore.product.modules.payment.service;

import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.dto.VnPayIpnResponse;

import java.util.Map;

public interface VnPayPaymentService {
    PaymentCallbackResult verifyReturn(Map<String, String> payload);
    VnPayIpnResponse processIpn(Map<String, String> payload);
}
