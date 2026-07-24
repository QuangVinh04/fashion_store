package com.fashionstore.product.modules.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.dto.VnPayIpnResponse;
import com.fashionstore.product.modules.payment.service.VnPayPaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnPayPaymentController {

    VnPayPaymentService vnPayPaymentService;

    @GetMapping("/return")
    public ApiResponse<PaymentCallbackResult> verifyReturn(@RequestParam Map<String, String> payload) {
        return ApiResponse.<PaymentCallbackResult>builder()
                .message("Verify VNPay return successfully")
                .data(vnPayPaymentService.verifyReturn(payload))
                .build();
    }

    @GetMapping("/ipn")
    public VnPayIpnResponse processIpn(@RequestParam Map<String, String> payload) {
        return vnPayPaymentService.processIpn(payload);
    }
}
