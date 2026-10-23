package com.fashionstore.payment.wallet.controller;
import com.fashionstore.payment.shared.util.AdminPaging;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.*;
import com.fashionstore.payment.wallet.dto.WalletTopUpResponse;
import com.fashionstore.payment.wallet.dto.WalletTransactionResponse;
import com.fashionstore.payment.wallet.entity.WalletAccount;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.wallet.repository.WalletAccountRepository;
import com.fashionstore.payment.wallet.repository.WalletLedgerEntryRepository;
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
        Page<WalletAccount> rows=userId==null || userId.isBlank()?wallets.findAll(AdminPaging.of(page,size))
                :wallets.findByUserId(userId,AdminPaging.of(page,size));
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
                userId,AdminPaging.of(page,size)).map(WalletTransactionResponse::from)).build();
    }
    @GetMapping("/{userId}/top-ups")
    public ApiResponse<Page<WalletTopUpResponse>> topUps(@PathVariable String userId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.<Page<WalletTopUpResponse>>builder().data(payments.findByUserIdAndTypeOrderByCreatedAtDesc(
                userId, com.fashionstore.common.payment.PaymentType.TOP_UP, AdminPaging.of(page,size)).map(WalletTopUpResponse::from)).build();
    }
    @GetMapping("/{userId}/transactions/{entryId}")
    public ApiResponse<WalletTransactionResponse> transaction(@PathVariable String userId,@PathVariable String entryId) {
        var entry=entries.findById(entryId).filter(row -> userId.equals(row.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        return ApiResponse.<WalletTransactionResponse>builder().data(WalletTransactionResponse.from(entry)).build();
    }
}
