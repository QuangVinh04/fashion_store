package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.service.CallbackPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * PayOS chỉ cần HTTP 2xx để coi là đã nhận webhook — không có mã phản hồi số như VNPay, nên luôn trả
 * 200 kèm outcome để debug qua log/response, không throw để tránh PayOS lặp lại webhook vô ích khi lỗi
 * là do dữ liệu đơn hàng chứ không phải do phía chúng ta.
 */
@RestController
@RequestMapping("/api/v1/payments/payos")
@Tag(name = "PayOS", description = "Return và Webhook xác nhận thanh toán của PayOS")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PayOsPaymentController {

    CallbackPaymentService callbackPaymentService;

    @GetMapping("/return")
    public ApiResponse<PaymentCallbackResult> verifyReturn(@RequestParam Map<String, String> payload) {
        return ApiResponse.<PaymentCallbackResult>builder()
                .message("Verify PayOS return successfully")
                .data(callbackPaymentService.verifyReturn(PaymentProvider.PAYOS, payload))
                .build();
    }

    /*
     * Webhook Callback Server-to-Server từ PayOS.
     * Tạm thời comment lại do đang chạy môi trường nội bộ / chưa có domain HTTPS công khai để PayOS gọi tới localhost.
     * Khi triển khai production có domain HTTPS, chỉ cần bỏ comment method này.
     */
    // @PostMapping("/webhook")
    // public ApiResponse<Void> webhook(@RequestBody String rawBody) {
    //     CallbackProcessResult result = callbackPaymentService.processCallback(PaymentProvider.PAYOS, Map.of(), rawBody);
    //     log.info("[PayOS] webhook processed with outcome={}", result.outcome());
    //     return ApiResponse.<Void>builder()
    //             .message("Webhook processed: " + result.outcome())
    //             .build();
    // }
}
