package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Tra cứu và khởi tạo thanh toán của khách hàng")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {

    PaymentService paymentService;

    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrderId(@PathVariable String orderId) {
        return ApiResponse.<PaymentResponse>builder()
                .message("Get payment successfully")
                .data(paymentService.getByOrderId(orderId))
                .build();
    }

    @PostMapping("/{id}/initiate")
    public ApiResponse<PaymentInitiationResult> initiate(@PathVariable("id") String id,
                                                         HttpServletRequest request) {
        return ApiResponse.<PaymentInitiationResult>builder()
                .message("Initiate payment successfully")
                .data(paymentService.initiate(id, request.getRemoteAddr()))
                .build();
    }

}
