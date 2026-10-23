package com.fashionstore.payment.payment.controller;
import com.fashionstore.payment.shared.util.AdminPaging;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.*;
import com.fashionstore.payment.payment.dto.AdminPaymentResponse;
import com.fashionstore.payment.payment.dto.AdminRefundResponse;
import com.fashionstore.payment.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/payments") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    @GetMapping("/order/{orderId}") @Transactional(readOnly=true)
    public ApiResponse<AdminPaymentResponse> payment(@PathVariable String orderId) {
        return ApiResponse.<AdminPaymentResponse>builder().data(AdminPaymentResponse.from(payments.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(com.fashionstore.payment.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND)))).build();
    }
    @GetMapping("/order/{orderId}/refunds") @Transactional(readOnly=true)
    public ApiResponse<Page<AdminRefundResponse>> refunds(@PathVariable String orderId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        String paymentId=payments.findByOrderId(orderId).orElseThrow(() -> new AppException(
                com.fashionstore.payment.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND)).getId();
        return ApiResponse.<Page<AdminRefundResponse>>builder().data(refunds.findByPaymentIdOrderByCreatedAtDescIdDesc(
                paymentId,AdminPaging.of(page,size)).map(AdminRefundResponse::from)).build();
    }
}
