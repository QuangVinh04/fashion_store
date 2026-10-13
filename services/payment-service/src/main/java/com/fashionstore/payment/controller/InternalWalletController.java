package com.fashionstore.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.payment.dto.RefundWalletRequest;
import com.fashionstore.payment.dto.RefundWalletResponse;
import com.fashionstore.payment.service.WalletService;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletService walletService;

    @PostMapping("/{userId}/refund")
    @PreAuthorize("hasRole('INTERNAL_CALLER')")
    public ApiResponse<RefundWalletResponse> refundToWallet(
            @PathVariable String userId,
            @Valid @RequestBody RefundWalletRequest request
    ) {
        return ApiResponse.<RefundWalletResponse>builder()
                .data(walletService.refundReturn(userId, request))
                .build();
    }
}
