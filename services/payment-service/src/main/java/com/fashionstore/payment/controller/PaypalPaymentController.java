package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.service.PaypalPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "PayPal", description = "Capture giao dịch PayPal sau khi khách phê duyệt")
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
