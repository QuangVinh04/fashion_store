package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.dto.CallbackOutcome;
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
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Chỉ xác nhận webhook sau khi transaction cập nhật payment/outbox đã commit.
 * Lỗi DB được trả về như lỗi server để provider có thể gửi lại.
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

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> webhook(@RequestBody String rawBody) {
        CallbackProcessResult result = callbackPaymentService.processCallback(PaymentProvider.PAYOS, Map.of(), rawBody);
        log.info("[PayOS] webhook processed with outcome={}", result.outcome());
        int status = result.outcome() == CallbackOutcome.SIGNATURE_INVALID
                || result.outcome() == CallbackOutcome.INVALID_REQUEST ? 400 : 200;
        // PayOS gửi callback mẫu khi đăng ký URL: đã xác minh chữ ký nhưng không có payment là hợp lệ.
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .message("Webhook processed: " + result.outcome()).build());
    }
}
