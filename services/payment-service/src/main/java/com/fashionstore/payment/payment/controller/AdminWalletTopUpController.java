package com.fashionstore.payment.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.payment.payment.dto.WalletTopUpResponse;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.shared.util.AdminPaging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/wallets") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
public class AdminWalletTopUpController {
    private final PaymentRepository payments;

    @GetMapping("/{userId}/top-ups")
    public ApiResponse<Page<WalletTopUpResponse>> topUps(@PathVariable String userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.<Page<WalletTopUpResponse>>builder().data(payments.findByUserIdAndTypeOrderByCreatedAtDesc(
                userId, PaymentType.TOP_UP, AdminPaging.of(page,size)).map(WalletTopUpResponse::from)).build();
    }
}
