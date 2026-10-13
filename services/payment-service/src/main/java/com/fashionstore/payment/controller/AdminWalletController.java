package com.fashionstore.payment.controller;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.*;
import com.fashionstore.payment.dto.*;
import com.fashionstore.payment.entity.WalletAccount;
import com.fashionstore.payment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/wallets") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
public class AdminWalletController {
    private final WalletAccountRepository wallets;
    private final WalletLedgerEntryRepository entries;
    private final PaymentRepository payments;
    public record Account(String userId,String currency,String balance) {
        static Account from(WalletAccount row) { return new Account(row.getUserId(),row.getCurrency(),Long.toString(row.getBalance())); }
    }
    @GetMapping
    public ApiResponse<Page<Account>> list(@RequestParam(required=false) String userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        Page<WalletAccount> rows=userId==null || userId.isBlank()?wallets.findAll(AdminPaymentController.paging(page,size))
                :wallets.findByUserId(userId,AdminPaymentController.paging(page,size));
        return ApiResponse.<Page<Account>>builder().data(rows
                .map(Account::from)).build();
    }
    @GetMapping("/{userId}")
    public ApiResponse<Account> detail(@PathVariable String userId) {
        return ApiResponse.<Account>builder().data(Account.from(wallets.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND)))).build();
    }
    @GetMapping("/{userId}/transactions")
    public ApiResponse<Page<WalletTransactionResponse>> transactions(@PathVariable String userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.<Page<WalletTransactionResponse>>builder().data(entries.findByUserIdOrderByCreatedAtDescIdDesc(
                userId,AdminPaymentController.paging(page,size)).map(WalletTransactionResponse::from)).build();
    }
    @GetMapping("/{userId}/top-ups")
    public ApiResponse<Page<WalletTopUpResponse>> topUps(@PathVariable String userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.<Page<WalletTopUpResponse>>builder().data(payments.findByUserIdAndTypeOrderByCreatedAtDesc(
                userId, com.fashionstore.common.payment.PaymentType.TOP_UP, AdminPaymentController.paging(page,size)).map(WalletTopUpResponse::from)).build();
    }
    @GetMapping("/{userId}/transactions/{entryId}")
    public ApiResponse<WalletTransactionResponse> transaction(@PathVariable String userId,@PathVariable String entryId) {
        var entry=entries.findById(entryId).filter(row -> userId.equals(row.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        return ApiResponse.<WalletTransactionResponse>builder().data(WalletTransactionResponse.from(entry)).build();
    }
}
