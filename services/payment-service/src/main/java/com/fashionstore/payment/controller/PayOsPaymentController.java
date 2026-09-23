package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.service.CallbackPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * PayOS chỉ cần HTTP 2xx để coi là đã nhận webhook — không có mã phản hồi số như VNPay, nên luôn trả
 * 200 kèm outcome để debug qua log/response, không throw để tránh PayOS lặp lại webhook vô ích khi lỗi
 * là do dữ liệu đơn hàng chứ không phải do phía chúng ta.
 */
@RestController
@RequestMapping("/api/v1/payments/payos")
@Tag(name = "PayOS", description = "Webhook xác nhận thanh toán của PayOS")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PayOsPaymentController {

    CallbackPaymentService callbackPaymentService;

    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(@RequestBody String rawBody) {
        CallbackProcessResult result = callbackPaymentService.processCallback(PaymentProvider.PAYOS, Map.of(), rawBody);
        log.info("[PayOS] webhook processed with outcome={}", result.outcome());
        return ApiResponse.<Void>builder()
                .message("Webhook processed: " + result.outcome())
                .build();
    }
}
