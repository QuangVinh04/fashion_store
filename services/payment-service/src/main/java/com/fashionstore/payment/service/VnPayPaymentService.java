package com.fashionstore.payment.service;

import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.VnPayIpnResponse;

import java.util.Map;

public interface VnPayPaymentService {
    PaymentCallbackResult verifyReturn(Map<String, String> payload);
    VnPayIpnResponse processIpn(Map<String, String> payload);
}
