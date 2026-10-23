package com.fashionstore.payment.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.payment.payment.dto.ConfirmWalletPaymentRequest;
import com.fashionstore.payment.payment.dto.WalletPaymentSession;
import com.fashionstore.payment.payment.service.WalletPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/payments") @RequiredArgsConstructor
public class WalletPaymentController {
    private final WalletPaymentService service;
    @GetMapping("/{id}/wallet-session")
    public ApiResponse<WalletPaymentSession> session(@PathVariable String id) {
        return ApiResponse.<WalletPaymentSession>builder().data(service.getSession(id)).build();
    }
    @PostMapping("/{id}/wallet-confirm")
    public ApiResponse<WalletPaymentSession> confirm(@PathVariable String id,@Valid @RequestBody ConfirmWalletPaymentRequest request) {
        return ApiResponse.<WalletPaymentSession>builder().data(service.confirm(id,request.attemptGeneration())).build();
    }
}
