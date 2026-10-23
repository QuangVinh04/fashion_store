package com.fashionstore.payment.wallet.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.wallet.dto.CreateWalletTopUpRequest;
import com.fashionstore.payment.wallet.dto.WalletResponse;
import com.fashionstore.payment.wallet.dto.WalletTopUpResponse;
import com.fashionstore.payment.wallet.dto.WalletTransactionResponse;
import com.fashionstore.payment.wallet.repository.WalletLedgerEntryRepository;
import com.fashionstore.payment.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/wallet") @RequiredArgsConstructor
public class WalletController {
    private final CurrentUserProvider currentUser;
    private final WalletService walletService;
    private final WalletLedgerEntryRepository entries;
    @Value("${payment.wallet.min-top-up-amount:10000}") private long minimum;
    @Value("${payment.wallet.max-top-up-amount:5000000}") private long maximum;
    @Value("${payment.wallet.top-up-enabled:false}") private boolean topUpEnabled;

    @GetMapping
    public ApiResponse<WalletResponse> get() {
        return ApiResponse.<WalletResponse>builder().data(new WalletResponse("VND",
                Long.toString(walletService.getBalance(currentUser.getCurrentUserId())),Long.toString(minimum),
                Long.toString(maximum),topUpEnabled)).build();
    }
    @GetMapping("/transactions")
    public ApiResponse<Page<WalletTransactionResponse>> transactions(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ApiResponse.<Page<WalletTransactionResponse>>builder().data(entries
                .findByUserIdOrderByCreatedAtDescIdDesc(currentUser.getCurrentUserId(),
                        PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size))))
                .map(WalletTransactionResponse::from)).build();
    }
    @PostMapping("/top-ups")
    public ApiResponse<WalletTopUpResponse> create(@Valid @RequestBody CreateWalletTopUpRequest request) {
        return ApiResponse.<WalletTopUpResponse>builder().data(walletService.createTopUp(request)).build();
    }
    @GetMapping("/top-ups/{id}")
    public ApiResponse<WalletTopUpResponse> topUp(@PathVariable String id) {
        return ApiResponse.<WalletTopUpResponse>builder().data(walletService.getTopUp(id)).build();
    }
}
