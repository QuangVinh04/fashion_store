package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackOutcome;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.VnPayIpnResponse;
import com.fashionstore.payment.service.CallbackPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "VNPay", description = "Return URL và IPN callback của VNPay")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnPayPaymentController {

    CallbackPaymentService callbackPaymentService;

    @GetMapping("/return")
    public ApiResponse<PaymentCallbackResult> verifyReturn(@RequestParam Map<String, String> payload) {
        return ApiResponse.<PaymentCallbackResult>builder()
                .message("Verify VNPay return successfully")
                .data(callbackPaymentService.verifyReturn(PaymentProvider.VNPAY, payload))
                .build();
    }

    @GetMapping("/ipn")
    public VnPayIpnResponse processIpn(@RequestParam Map<String, String> payload) {
        CallbackProcessResult result = callbackPaymentService.processCallback(PaymentProvider.VNPAY, payload, null);
        return switch (result.outcome()) {
            case INVALID_REQUEST -> new VnPayIpnResponse("99", "Invalid request");
            case SIGNATURE_INVALID -> VnPayIpnResponse.invalidChecksum();
            case PAYMENT_NOT_FOUND -> VnPayIpnResponse.orderNotFound();
            case AMOUNT_INVALID -> VnPayIpnResponse.invalidAmount();
            case ALREADY_PROCESSED -> VnPayIpnResponse.alreadyConfirmed();
            case APPLIED -> VnPayIpnResponse.success();
        };
    }
}
