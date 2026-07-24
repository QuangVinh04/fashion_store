package com.fashionstore.product.modules.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.product.modules.payment.dto.PaymentResponse;
import com.fashionstore.product.modules.payment.service.PaypalPaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalPaymentController {

    PaypalPaymentService paypalPaymentService;

    @PostMapping("/{id}/paypal/capture")
    public ApiResponse<PaymentResponse> capture(@PathVariable("id") String id) {
        return ApiResponse.<PaymentResponse>builder()
                .message("Capture PayPal payment successfully")
                .data(paypalPaymentService.capture(id))
                .build();
    }
}
