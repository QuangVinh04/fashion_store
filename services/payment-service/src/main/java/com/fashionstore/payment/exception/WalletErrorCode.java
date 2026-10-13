package com.fashionstore.payment.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum WalletErrorCode implements BaseErrorCode {
    INSUFFICIENT_BALANCE(5101, "Wallet balance is insufficient", HttpStatus.CONFLICT),
    OPERATION_CONFLICT(5102, "Wallet operation was already used for a different request", HttpStatus.CONFLICT),
    AMOUNT_INVALID(5103, "Wallet amount must be a positive integer VND amount", HttpStatus.BAD_REQUEST),
    SOURCE_INVALID(5104, "Wallet operation source is invalid", HttpStatus.BAD_REQUEST);
    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
    WalletErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code; this.message = message; this.statusCode = statusCode;
    }
}
