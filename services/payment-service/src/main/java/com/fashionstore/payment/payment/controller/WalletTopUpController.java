package com.fashionstore.payment.payment.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.payment.payment.dto.CreateWalletTopUpRequest;
import com.fashionstore.payment.payment.dto.WalletTopUpResponse;
import com.fashionstore.payment.payment.service.WalletTopUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/wallet") @RequiredArgsConstructor
public class WalletTopUpController {
    private final WalletTopUpService topUpService;

    @PostMapping("/top-ups")
    public ApiResponse<WalletTopUpResponse> create(@Valid @RequestBody CreateWalletTopUpRequest request) {
        return ApiResponse.<WalletTopUpResponse>builder().data(topUpService.createTopUp(request)).build();
    }
    @GetMapping("/top-ups/{id}")
    public ApiResponse<WalletTopUpResponse> topUp(@PathVariable String id) {
        return ApiResponse.<WalletTopUpResponse>builder().data(topUpService.getTopUp(id)).build();
    }
}
